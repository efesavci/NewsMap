"""Jina embeddings model adapter."""

from __future__ import annotations

import logging

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer

logger = logging.getLogger(__name__)


class JinaEmbedder:
    """Adapter exposing a simple `embed(texts)` interface."""

    def __init__(self, model_name: str, trust_remote_code: bool = True, max_length: int = 4096) -> None:
        self.model_name = model_name
        self.trust_remote_code = trust_remote_code
        self.max_length = max_length

        logger.info("Loading embedder model: %s", model_name)
        self.tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=trust_remote_code)
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
        """Generate embeddings by mean-pooling last hidden state."""
        if batch_size <= 0:
            raise ValueError("batch_size must be positive")

        vectors: list[np.ndarray] = []
        for idx in range(0, len(texts), batch_size):
            batch = texts[idx : idx + batch_size]
            inputs = self.tokenizer(
                batch,
                return_tensors="pt",
                truncation=True,
                padding=True,
                max_length=self.max_length,
            ).to(self.device)
            with torch.no_grad():
                outputs = self.model(**inputs)
            pooled = outputs.last_hidden_state.mean(dim=1)
            vectors.append(pooled.cpu().numpy())
        return np.vstack(vectors)
