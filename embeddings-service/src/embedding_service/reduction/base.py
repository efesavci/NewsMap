"""Base classes for reducers."""

from __future__ import annotations

from abc import ABC, abstractmethod

import numpy as np


class Reducer(ABC):
    """Common interface to support interchangeable reducers."""

    @abstractmethod
    def fit_transform(self, vectors: np.ndarray) -> np.ndarray:
        """Fit reducer and return transformed vectors."""
