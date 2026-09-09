"""SQLite persistence and JSON projection for stable news events."""

from __future__ import annotations

import json
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from embedding_service.events.event_matcher import normalize, title_entities, title_tokens


SCHEMA = """
CREATE TABLE IF NOT EXISTS events (
    event_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    first_published_at TEXT NOT NULL,
    last_published_at TEXT NOT NULL,
    article_count INTEGER NOT NULL,
    confidence_sum REAL NOT NULL,
    centroid BLOB NOT NULL,
    centroid_dim INTEGER NOT NULL,
    title_tokens TEXT NOT NULL,
    entities TEXT NOT NULL,
    sources TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS event_articles (
    article_id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES events(event_id),
    title TEXT NOT NULL,
    source TEXT NOT NULL,
    url TEXT,
    publish_time TEXT NOT NULL,
    matched_at TEXT NOT NULL,
    match_score REAL NOT NULL,
    match_signals TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_articles_event_id ON event_articles(event_id);
CREATE INDEX IF NOT EXISTS idx_events_last_published_at ON events(last_published_at);
CREATE TABLE IF NOT EXISTS event_updates (
    update_id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES events(event_id),
    update_type TEXT NOT NULL,
    created_at TEXT NOT NULL,
    article_id TEXT,
    summary TEXT NOT NULL,
    metadata TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_updates_event_id ON event_updates(event_id, created_at);
INSERT OR IGNORE INTO event_updates (
    update_id, event_id, update_type, created_at, article_id, summary, metadata
)
SELECT 'upd_seed_' || event_id, event_id, 'CREATED', created_at, NULL,
       'Event created from its first report', '{}'
FROM events AS event
WHERE NOT EXISTS (
    SELECT 1 FROM event_updates AS update_record
    WHERE update_record.event_id = event.event_id AND update_record.update_type = 'CREATED'
);
"""


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


class EventStore:
    def __init__(self, path: str) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.path)
        self.connection.row_factory = sqlite3.Row
        self.connection.executescript(SCHEMA)

    def close(self) -> None:
        self.connection.close()

    def __enter__(self) -> "EventStore":
        return self

    def __exit__(self, *_args) -> None:
        self.close()

    def has_article(self, article_id: str) -> bool:
        row = self.connection.execute(
            "SELECT 1 FROM event_articles WHERE article_id = ?", (article_id,)
        ).fetchone()
        return row is not None

    def active_events(self) -> list[dict]:
        rows = self.connection.execute("SELECT * FROM events WHERE status = 'ACTIVE'").fetchall()
        return [self._event_for_matching(row) for row in rows]

    def matchable_events(self) -> list[dict]:
        rows = self.connection.execute(
            "SELECT * FROM events WHERE status IN ('ACTIVE', 'DORMANT')"
        ).fetchall()
        return [self._event_for_matching(row) for row in rows]

    def create_event(self, *, event_id: str, article: dict, vector: np.ndarray) -> None:
        now = utc_now()
        published = article["publish_time"]
        normalized = normalize(vector)
        tokens = sorted(title_tokens(article["title"]))
        entities = sorted(title_entities(article["title"]))
        self.connection.execute(
            """INSERT INTO events (
                event_id, title, status, created_at, updated_at,
                first_published_at, last_published_at, article_count, confidence_sum,
                centroid, centroid_dim, title_tokens, entities, sources
            ) VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?, 1, 1.0, ?, ?, ?, ?, ?)""",
            (
                event_id, article["title"], now, now, published, published,
                normalized.astype("float32").tobytes(), normalized.size,
                json.dumps(tokens), json.dumps(entities), json.dumps([article["source"]]),
            ),
        )
        self._insert_article(event_id, article, 1.0, {"seed": 1.0})
        self._append_update(
            event_id=event_id,
            update_type="CREATED",
            article_id=article["article_id"],
            summary="Event created from its first report",
            metadata={"source": article["source"]},
        )
        self.connection.commit()

    def attach_article(
        self,
        *,
        event_id: str,
        article: dict,
        vector: np.ndarray,
        score: float,
        signals: dict[str, float],
    ) -> None:
        row = self.connection.execute("SELECT * FROM events WHERE event_id = ?", (event_id,)).fetchone()
        if row is None:
            raise KeyError(f"Unknown event: {event_id}")

        old_count = int(row["article_count"])
        old_centroid = self._decode_centroid(row)
        centroid = normalize((old_centroid * old_count + normalize(vector)) / (old_count + 1))
        tokens = set(json.loads(row["title_tokens"])) | title_tokens(article["title"])
        entities = set(json.loads(row["entities"])) | title_entities(article["title"])
        old_sources = set(json.loads(row["sources"]))
        sources = old_sources | {article["source"]}
        first_published = min(row["first_published_at"], article["publish_time"])
        last_published = max(row["last_published_at"], article["publish_time"])

        self.connection.execute(
            """UPDATE events SET status = 'ACTIVE', updated_at = ?, first_published_at = ?, last_published_at = ?,
                article_count = ?, confidence_sum = ?, centroid = ?, centroid_dim = ?,
                title_tokens = ?, entities = ?, sources = ? WHERE event_id = ?""",
            (
                utc_now(), first_published, last_published, old_count + 1,
                float(row["confidence_sum"]) + score, centroid.astype("float32").tobytes(), centroid.size,
                json.dumps(sorted(tokens)), json.dumps(sorted(entities)), json.dumps(sorted(sources)), event_id,
            ),
        )
        self._insert_article(event_id, article, score, signals)
        self._append_update(
            event_id=event_id,
            update_type="REPORT_ADDED",
            article_id=article["article_id"],
            summary=f"New report from {article['source']}",
            metadata={
                "source": article["source"],
                "sourceAdded": article["source"] not in old_sources,
                "matchScore": round(score, 6),
            },
        )
        if row["status"] != "ACTIVE":
            self._append_update(
                event_id=event_id,
                update_type="REOPENED",
                article_id=article["article_id"],
                summary="Event became active after a new matching report",
                metadata={"previousStatus": row["status"]},
            )
        self.connection.commit()

    def refresh_lifecycle(
        self,
        *,
        reference_time: datetime,
        dormant_after_hours: int = 72,
        archive_after_hours: int = 336,
    ) -> dict[str, int]:
        counts = {"active": 0, "dormant": 0, "archived": 0, "changed": 0}
        rows = self.connection.execute("SELECT * FROM events").fetchall()
        for row in rows:
            last_published = datetime.fromisoformat(row["last_published_at"].replace("Z", "+00:00"))
            age_hours = max(0.0, (reference_time - last_published).total_seconds() / 3600)
            if age_hours >= archive_after_hours:
                target = "ARCHIVED"
            elif age_hours >= dormant_after_hours:
                target = "DORMANT"
            else:
                target = "ACTIVE"
            counts[target.lower()] += 1
            if row["status"] == target:
                continue
            self.connection.execute(
                "UPDATE events SET status = ?, updated_at = ? WHERE event_id = ?",
                (target, utc_now(), row["event_id"]),
            )
            self._append_update(
                event_id=row["event_id"],
                update_type="STATUS_CHANGED",
                article_id=None,
                summary=f"Event changed from {row['status']} to {target}",
                metadata={"from": row["status"], "to": target, "ageHours": round(age_hours, 2)},
            )
            counts["changed"] += 1
        self.connection.commit()
        return counts

    def export_json(self, path: str, engine_version: str = "persistent-events-v1") -> dict:
        output = Path(path)
        output.parent.mkdir(parents=True, exist_ok=True)
        events = []
        for row in self.connection.execute(
            "SELECT * FROM events ORDER BY last_published_at DESC, event_id"
        ).fetchall():
            article_rows = self.connection.execute(
                "SELECT * FROM event_articles WHERE event_id = ? ORDER BY publish_time, article_id",
                (row["event_id"],),
            ).fetchall()
            update_rows = self.connection.execute(
                "SELECT * FROM event_updates WHERE event_id = ? ORDER BY created_at, update_id",
                (row["event_id"],),
            ).fetchall()
            events.append({
                "id": row["event_id"],
                "title": row["title"],
                "status": row["status"],
                "createdAt": row["created_at"],
                "updatedAt": row["updated_at"],
                "firstPublishedAt": row["first_published_at"],
                "lastPublishedAt": row["last_published_at"],
                "articleCount": row["article_count"],
                "confidence": round(float(row["confidence_sum"]) / int(row["article_count"]), 6),
                "sources": json.loads(row["sources"]),
                "entities": json.loads(row["entities"]),
                "updates": [{
                    "updateId": update["update_id"],
                    "type": update["update_type"],
                    "createdAt": update["created_at"],
                    "articleId": update["article_id"],
                    "summary": update["summary"],
                    "metadata": json.loads(update["metadata"]),
                } for update in update_rows],
                "articles": [{
                    "articleId": article["article_id"],
                    "title": article["title"],
                    "source": article["source"],
                    "url": article["url"],
                    "publishTime": article["publish_time"],
                    "matchScore": round(float(article["match_score"]), 6),
                    "matchSignals": json.loads(article["match_signals"]),
                } for article in article_rows],
            })

        projection = {"generatedAt": utc_now(), "engineVersion": engine_version, "events": events}
        output.write_text(json.dumps(projection, indent=2, ensure_ascii=False), encoding="utf-8")
        return projection

    def _append_update(
        self,
        *,
        event_id: str,
        update_type: str,
        article_id: str | None,
        summary: str,
        metadata: dict,
    ) -> None:
        self.connection.execute(
            """INSERT INTO event_updates (
                update_id, event_id, update_type, created_at, article_id, summary, metadata
            ) VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (
                f"upd_{uuid.uuid4().hex}", event_id, update_type, utc_now(), article_id,
                summary, json.dumps(metadata, sort_keys=True),
            ),
        )

    def _insert_article(
        self, event_id: str, article: dict, score: float, signals: dict[str, float]
    ) -> None:
        self.connection.execute(
            """INSERT INTO event_articles (
                article_id, event_id, title, source, url, publish_time, matched_at, match_score, match_signals
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                article["article_id"], event_id, article["title"], article["source"], article.get("url"),
                article["publish_time"], utc_now(), score, json.dumps(signals, sort_keys=True),
            ),
        )

    def _event_for_matching(self, row: sqlite3.Row) -> dict:
        return {
            "event_id": row["event_id"],
            "last_published_at": row["last_published_at"],
            "centroid": self._decode_centroid(row),
            "title_tokens": json.loads(row["title_tokens"]),
            "entities": json.loads(row["entities"]),
        }

    @staticmethod
    def _decode_centroid(row: sqlite3.Row) -> np.ndarray:
        return np.frombuffer(row["centroid"], dtype=np.float32, count=int(row["centroid_dim"])).copy()
