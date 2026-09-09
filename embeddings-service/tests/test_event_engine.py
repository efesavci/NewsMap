from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np

from embedding_service.events.event_matcher import EventMatcher, title_entities, title_tokens
from embedding_service.events.event_store import EventStore
from embedding_service.pipelines.event_pipeline import stable_event_id


class EventMatcherTest(unittest.TestCase):
    def test_stable_event_id_is_repeatable(self) -> None:
        self.assertEqual(stable_event_id("article-1"), stable_event_id("article-1"))
        self.assertNotEqual(stable_event_id("article-1"), stable_event_id("article-2"))

    def test_high_similarity_paraphrase_matches(self) -> None:
        vector = np.array([1.0, 0.0, 0.0], dtype=np.float32)
        title = "French MPs approve assisted dying law with strict rules"
        candidate = {
            "event_id": "evt_1",
            "last_published_at": "2026-07-15T12:00:00+00:00",
            "centroid": np.array([0.98, 0.02, 0.0], dtype=np.float32),
            "title_tokens": title_tokens("French parliament passes assisted dying bill"),
            "entities": title_entities("French parliament passes assisted dying bill"),
        }
        decision = EventMatcher().match(
            vector=vector,
            title=title,
            published_at=datetime(2026, 7, 15, 13, tzinfo=timezone.utc),
            candidates=[candidate],
        )
        self.assertTrue(decision.accepted)
        self.assertEqual("evt_1", decision.event_id)

    def test_shared_entity_does_not_override_different_story(self) -> None:
        candidate = {
            "event_id": "evt_mexico_ice",
            "last_published_at": "2026-07-15T00:00:00+00:00",
            "centroid": np.array([1.0, 0.0, 0.0], dtype=np.float32),
            "title_tokens": title_tokens("Sheinbaum announces legal action over ICE custody deaths"),
            "entities": title_entities("Sheinbaum announces legal action over ICE custody deaths"),
        }
        decision = EventMatcher().match(
            vector=np.array([0.87, 0.49, 0.0], dtype=np.float32),
            title="Sheinbaum rejects US claim linking Mexico government to cartels",
            published_at=datetime(2026, 7, 15, 1, tzinfo=timezone.utc),
            candidates=[candidate],
        )
        self.assertFalse(decision.accepted)


class EventStoreTest(unittest.TestCase):
    def test_existing_article_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = str(Path(directory) / "events.db")
            article = {
                "article_id": "article-1",
                "title": "A test event happens",
                "source": "example.com",
                "url": "https://example.com/1",
                "publish_time": "2026-07-15T12:00:00+00:00",
            }
            with EventStore(database) as store:
                store.create_event(
                    event_id=stable_event_id(article["article_id"]),
                    article=article,
                    vector=np.array([1.0, 0.0], dtype=np.float32),
                )
                self.assertTrue(store.has_article("article-1"))
                self.assertEqual(1, len(store.active_events()))

    def test_lifecycle_and_updates_are_durable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = str(Path(directory) / "events.db")
            article = {
                "article_id": "article-1",
                "title": "Flooding strikes a test city",
                "source": "first.example",
                "url": "https://first.example/1",
                "publish_time": "2026-07-15T12:00:00+00:00",
            }
            event_id = stable_event_id(article["article_id"])
            with EventStore(database) as store:
                store.create_event(
                    event_id=event_id,
                    article=article,
                    vector=np.array([1.0, 0.0], dtype=np.float32),
                )
                lifecycle = store.refresh_lifecycle(
                    reference_time=datetime.fromisoformat(article["publish_time"]) + timedelta(hours=80)
                )
                self.assertEqual(1, lifecycle["dormant"])
                self.assertEqual(1, len(store.matchable_events()))

                followup = {
                    **article,
                    "article_id": "article-2",
                    "source": "second.example",
                    "url": "https://second.example/2",
                    "publish_time": "2026-07-19T00:00:00+00:00",
                }
                store.attach_article(
                    event_id=event_id,
                    article=followup,
                    vector=np.array([0.99, 0.01], dtype=np.float32),
                    score=0.91,
                    signals={"semantic": 0.98, "time": 0.8},
                )
                projection = store.export_json(str(Path(directory) / "events.json"))

            event = projection["events"][0]
            self.assertEqual("ACTIVE", event["status"])
            self.assertEqual(2, event["articleCount"])
            update_types = [update["type"] for update in event["updates"]]
            self.assertIn("CREATED", update_types)
            self.assertIn("STATUS_CHANGED", update_types)
            self.assertIn("REPORT_ADDED", update_types)
            self.assertIn("REOPENED", update_types)


if __name__ == "__main__":
    unittest.main()
