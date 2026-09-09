"""Jina embeddings model adapter."""

from __future__ import annotations

import logging

import numpy as np
import torch
from transformers import AutoModel

logger = logging.getLogger(__name__)


class JinaEmbedder:
    """Adapter exposing a simple `embed(texts)` interface."""

    def __init__(
        self,
        model_name: str,
        trust_remote_code: bool = True,
        max_length: int = 4096,
        task: str = "separation",
        truncate_dim: int = 512,
    ) -> None:
        self.model_name = model_name
        self.trust_remote_code = trust_remote_code
        self.max_length = max_length
        self.task = task
        self.truncate_dim = truncate_dim

        logger.info("Loading embedder model: %s", model_name)
        self.model = AutoModel.from_pretrained(
            model_name,
            trust_remote_code=trust_remote_code,
            dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
        )
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model.to(self.device)
        self.model.eval()
        logger.info("Embedder ready on device=%s", self.device)

    def embed(self, texts: list[str], batch_size: int) -> np.ndarray:
        """Generate normalized embeddings using Jina's clustering adapter."""
        if batch_size <= 0:
            raise ValueError("batch_size must be positive")

        vectors: list[np.ndarray] = []
        for idx in range(0, len(texts), batch_size):
            batch = texts[idx : idx + batch_size]
            with torch.no_grad():
                encoded = self.model.encode(
                    batch,
                    task=self.task,
                    max_length=self.max_length,
                    truncate_dim=self.truncate_dim,
                )
            if isinstance(encoded, torch.Tensor):
                encoded = encoded.detach().cpu().numpy()
            array = np.asarray(encoded, dtype=np.float32)
            norms = np.linalg.norm(array, axis=1, keepdims=True)
            vectors.append(array / np.clip(norms, a_min=1e-12, a_max=None))
        return np.vstack(vectors)
