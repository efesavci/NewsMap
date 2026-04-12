"""DBSCAN clusterer adapter."""

from __future__ import annotations

import numpy as np
from sklearn.cluster import DBSCAN

from embedding_service.clustering.base import Clusterer


class DbscanClusterer(Clusterer):
    def __init__(self, eps: float, min_samples: int, metric: str = "euclidean") -> None:
        self.model = DBSCAN(eps=eps, min_samples=min_samples, metric=metric)

    def fit_predict(self, vectors: np.ndarray) -> np.ndarray:
        return self.model.fit_predict(vectors)
