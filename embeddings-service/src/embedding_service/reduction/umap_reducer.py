"""UMAP reducer implementation."""

from __future__ import annotations

import numpy as np
import umap

from embedding_service.reduction.base import Reducer


class UmapReducer(Reducer):
    """UMAP-based reducer implementing standard reducer interface."""

    def __init__(self, n_components: int, n_neighbors: int, min_dist: float, metric: str, random_state: int | None = None) -> None:
        self.reducer = umap.UMAP(
            n_components=n_components,
            n_neighbors=n_neighbors,
            min_dist=min_dist,
            metric=metric,
            random_state=random_state,
        )

    def fit_transform(self, vectors: np.ndarray) -> np.ndarray:
        return self.reducer.fit_transform(vectors)
