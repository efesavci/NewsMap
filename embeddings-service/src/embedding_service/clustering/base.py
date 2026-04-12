"""Base classes for clusterers."""

from __future__ import annotations

from abc import ABC, abstractmethod

import numpy as np


class Clusterer(ABC):
    """Common interface to support interchangeable clustering methods."""

    @abstractmethod
    def fit_predict(self, vectors: np.ndarray) -> np.ndarray:
        """Return cluster labels for each input vector."""
