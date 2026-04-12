"""Cluster quality metrics."""

from __future__ import annotations

import numpy as np
from sklearn.metrics import silhouette_score


def compute_cluster_metrics(vectors: np.ndarray, labels: np.ndarray) -> dict[str, float | int | None]:
    """Compute basic clustering metrics with noise-awareness."""
    n_noise = int(np.sum(labels == -1))
    unique_labels = {int(x) for x in labels if int(x) != -1}
    metrics: dict[str, float | int | None] = {
        "num_points": int(labels.shape[0]),
        "num_clusters": len(unique_labels),
        "num_noise_points": n_noise,
        "silhouette_score": None,
    }
    if len(unique_labels) > 1:
        mask = labels != -1
        if np.sum(mask) > 2:
            metrics["silhouette_score"] = float(silhouette_score(vectors[mask], labels[mask]))
    return metrics
