"""In-memory vector shortlist, refreshed as persistent events change."""
from __future__ import annotations

from datetime import datetime
import numpy as np
from embedding_service.events.event_matcher import normalize, parse_timestamp


class EventCandidateIndex:
    def __init__(self, events: list[dict], dimension: int):
        self.dimension = dimension
        self.positions: dict[str, int] = {}
        self.events: list[dict] = []
        self.vectors = np.empty((max(16, len(events)), dimension), dtype=np.float32)
        self.times = np.empty(len(self.vectors), dtype=np.float64)
        for event in events:
            self.update(event)

    def update(self, event: dict) -> None:
        vector = normalize(event['centroid'])
        if vector.shape != (self.dimension,):
            raise ValueError('Stored event dimensions differ from incoming embeddings; use the same model and dimensions.')
        position = self.positions.get(event['event_id'])
        if position is None:
            position = len(self.events)
            if position == len(self.vectors):
                self.vectors = np.concatenate((self.vectors, np.empty_like(self.vectors)))
                self.times = np.concatenate((self.times, np.empty_like(self.times)))
            self.positions[event['event_id']] = position
            self.events.append(event)
        else:
            self.events[position] = event
        self.vectors[position] = vector
        self.times[position] = parse_timestamp(event['last_published_at']).timestamp()

    def shortlist(self, vector: np.ndarray, published_at: datetime, window_hours: int) -> list[dict]:
        count = len(self.events)
        if not count:
            return []
        # All candidates meeting the matcher's mandatory cosine floor are retained.
        # No top-k cutoff: a lexically stronger valid match cannot be dropped.
        cosine = self.vectors[:count] @ normalize(vector)
        recent = np.abs(self.times[:count] - published_at.timestamp()) <= window_hours * 3600
        positions = np.flatnonzero(recent & (cosine >= 0.78 - 1e-6))
        return [self.events[int(position)] for position in positions]
