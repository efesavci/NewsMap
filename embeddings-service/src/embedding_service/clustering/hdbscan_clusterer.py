"""HDBSCAN clusterer adapter."""

from __future__ import annotations

import hdbscan
import numpy as np

from embedding_service.clustering.base import Clusterer


class HdbscanClusterer(Clusterer):
    def __init__(
        self,
        min_cluster_size: int,
        min_samples: int | None = None,
        metric: str = "euclidean",
        cluster_selection_method: str = "eom",
    ) -> None:
        self.model = hdbscan.HDBSCAN(
            min_cluster_size=min_cluster_size,
            min_samples=min_samples,
            metric=metric,
            cluster_selection_method=cluster_selection_method,
        )

    def fit_predict(self, vectors: np.ndarray) -> np.ndarray:
        return self.model.fit_predict(vectors)

    @property
    def probabilities(self) -> np.ndarray:
        return self.model.probabilities_
