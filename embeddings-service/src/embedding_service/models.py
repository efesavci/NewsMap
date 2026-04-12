"""Shared typed models used across pipelines."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class Article:
    """Canonical in-memory article representation used for embedding."""

    body: str
    article_id: str | None = None
    title: str | None = None
    source: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class EmbeddingRecord:
    """Embedding row with associated metadata from HDF5 storage."""

    embedding_id: str
    vector: list[float]
    metadata: dict[str, Any]
