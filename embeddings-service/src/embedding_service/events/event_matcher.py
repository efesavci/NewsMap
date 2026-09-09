"""Incremental article-to-event matching using local, explainable signals."""

from __future__ import annotations

import math
import re
from dataclasses import dataclass
from datetime import datetime, timezone

import numpy as np


TOKEN_RE = re.compile(r"[\w'-]+", re.UNICODE)
ENTITY_RE = re.compile(r"\b(?:[A-Z][\w'-]+|[A-Z]{2,})(?:\s+(?:[A-Z][\w'-]+|[A-Z]{2,})){0,3}\b")
STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "been", "by", "for", "from", "has", "have",
    "how", "in", "is", "it", "its", "new", "of", "on", "or", "says", "that", "the", "their",
    "this", "to", "was", "what", "when", "where", "who", "why", "will", "with",
}


@dataclass(frozen=True, slots=True)
class MatchDecision:
    event_id: str | None
    score: float
    semantic_similarity: float
    lexical_similarity: float
    entity_similarity: float
    time_compatibility: float
    accepted: bool

    def signals(self) -> dict[str, float]:
        return {
            "semantic": round(self.semantic_similarity, 6),
            "lexical": round(self.lexical_similarity, 6),
            "entities": round(self.entity_similarity, 6),
            "time": round(self.time_compatibility, 6),
        }


def normalize(vector: np.ndarray) -> np.ndarray:
    vector = np.asarray(vector, dtype=np.float32)
    norm = float(np.linalg.norm(vector))
    return vector if norm == 0 else vector / norm


def title_tokens(title: str) -> set[str]:
    return {token.lower() for token in TOKEN_RE.findall(title) if len(token) > 2 and token.lower() not in STOPWORDS}


def title_entities(title: str) -> set[str]:
    entities = set()
    for match in ENTITY_RE.findall(title):
        normalized = match.strip().lower()
        if normalized and normalized not in STOPWORDS:
            entities.add(normalized)
    return entities


def jaccard(left: set[str], right: set[str]) -> float:
    if not left or not right:
        return 0.0
    return len(left & right) / len(left | right)


def parse_timestamp(value: str | None) -> datetime:
    if not value:
        return datetime.now(timezone.utc)
    normalized = value.strip().replace("Z", "+00:00")
    for candidate in (normalized, normalized.replace(" ", "T")):
        try:
            parsed = datetime.fromisoformat(candidate)
            return parsed.replace(tzinfo=parsed.tzinfo or timezone.utc).astimezone(timezone.utc)
        except ValueError:
            continue
    return datetime.now(timezone.utc)


class EventMatcher:
    """Scores one article against active persistent events."""

    def __init__(self, threshold: float = 0.82, active_window_hours: int = 336) -> None:
        self.threshold = threshold
        self.active_window_hours = active_window_hours

    def match(
        self,
        *,
        vector: np.ndarray,
        title: str,
        published_at: datetime,
        candidates: list[dict],
    ) -> MatchDecision:
        article_vector = normalize(vector)
        article_tokens = title_tokens(title)
        article_entities = title_entities(title)
        best = MatchDecision(None, 0, 0, 0, 0, 0, False)

        for event in candidates:
            event_time = parse_timestamp(event["last_published_at"])
            age_hours = abs((published_at - event_time).total_seconds()) / 3600
            if age_hours > self.active_window_hours:
                continue

            semantic = float(np.dot(article_vector, normalize(event["centroid"])))
            lexical = jaccard(article_tokens, set(event["title_tokens"]))
            entities = jaccard(article_entities, set(event["entities"]))
            time_score = math.exp(-age_hours / 72)
            score = 0.82 * semantic + 0.08 * lexical + 0.07 * entities + 0.03 * time_score

            # High cosine similarity is not enough by itself: require some
            # corroborating event evidence, except for near-duplicate wording.
            corroborated = lexical >= 0.10 or entities >= 0.12 or semantic >= 0.94
            strong_paraphrase = semantic >= 0.89 and (
                (lexical >= 0.18 and entities >= 0.25)
                or lexical >= 0.28
            )
            near_duplicate = semantic >= 0.96 and lexical >= 0.06
            accepted = semantic >= 0.78 and corroborated and (
                score >= self.threshold or strong_paraphrase or near_duplicate
            )
            decision = MatchDecision(
                event_id=event["event_id"],
                score=score,
                semantic_similarity=semantic,
                lexical_similarity=lexical,
                entity_similarity=entities,
                time_compatibility=time_score,
                accepted=accepted,
            )
            if decision.score > best.score:
                best = decision

        return best
