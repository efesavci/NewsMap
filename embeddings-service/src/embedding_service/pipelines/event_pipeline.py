"""Persistent event matching pipeline."""

from __future__ import annotations

import hashlib
import logging

from embedding_service.events.event_matcher import EventMatcher, parse_timestamp
from embedding_service.events.event_store import EventStore
from embedding_service.io.embeddings import load_embeddings_h5


logger = logging.getLogger(__name__)


def stable_event_id(article_id: str) -> str:
    digest = hashlib.sha256(article_id.encode("utf-8")).hexdigest()[:20]
    return f"evt_{digest}"


def run_event_pipeline(
    *,
    input_h5: str,
    state_db: str,
    output_json: str,
    threshold: float = 0.82,
    active_window_hours: int = 336,
) -> dict:
    vectors, article_ids, metadata = load_embeddings_h5(input_h5)
    records = []
    for vector, article_id, raw in zip(vectors, article_ids, metadata, strict=True):
        publish_time = str(raw.get("publishTime") or raw.get("crawledAt") or "")
        records.append((parse_timestamp(publish_time), article_id, vector, raw))
    records.sort(key=lambda item: (item[0], item[1]))

    matcher = EventMatcher(threshold=threshold, active_window_hours=active_window_hours)
    created = matched = skipped = 0
    with EventStore(state_db) as store:
        for published_at, article_id, vector, raw in records:
            if store.has_article(article_id):
                skipped += 1
                continue

            article = {
                "article_id": article_id,
                "title": str(raw.get("title") or "Untitled article"),
                "source": str(raw.get("source") or "Unknown source"),
                "url": str(raw.get("url") or ""),
                "publish_time": published_at.isoformat(),
            }
            decision = matcher.match(
                vector=vector,
                title=article["title"],
                published_at=published_at,
                candidates=store.matchable_events(),
            )
            if decision.accepted and decision.event_id:
                store.attach_article(
                    event_id=decision.event_id,
                    article=article,
                    vector=vector,
                    score=decision.score,
                    signals=decision.signals(),
                )
                matched += 1
            else:
                store.create_event(event_id=stable_event_id(article_id), article=article, vector=vector)
                created += 1

        reference_time = records[-1][0] if records else parse_timestamp("")
        lifecycle = store.refresh_lifecycle(reference_time=reference_time)
        projection = store.export_json(output_json, engine_version="persistent-events-v2")

    result = {
        "articlesSeen": len(records),
        "events": len(projection["events"]),
        "created": created,
        "matched": matched,
        "skipped": skipped,
        "lifecycle": lifecycle,
        "stateDb": state_db,
        "outputJson": output_json,
    }
    logger.info("Persistent event matching completed: %s", result)
    return result
