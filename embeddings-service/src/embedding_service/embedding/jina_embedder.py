"""Jina embeddings adapter with native Apple GPU support and bounded batches."""
from __future__ import annotations

import logging
import os
import numpy as np
import torch
from transformers import AutoModel

logger = logging.getLogger(__name__)


def select_device(requested: str = 'auto') -> str:
    requested = requested.lower()
    if requested not in {'auto', 'cpu', 'cuda', 'mps'}:
        raise ValueError('Embedding device must be auto, cpu, cuda or mps')
    available = {'cpu': True, 'cuda': torch.cuda.is_available(), 'mps': torch.backends.mps.is_available()}
    if requested != 'auto':
        if not available[requested]:
            raise ValueError(f'Requested embedding device is unavailable: {requested}')
        return requested
    return next(device for device in ('cuda', 'mps', 'cpu') if available[device])


class JinaEmbedder:
    def __init__(self, model_name: str, trust_remote_code: bool = True, max_length: int = 4096,
                 task: str = 'separation', truncate_dim: int = 512, device: str | None = None) -> None:
        self.model_name = model_name
        self.max_length = max_length
        self.task = task
        self.truncate_dim = truncate_dim
        self.device = select_device(device or os.environ.get('NEWSMAP_EMBEDDING_DEVICE', 'auto'))
        logger.info('Loading %s on %s', model_name, self.device)
        self.model = AutoModel.from_pretrained(model_name, trust_remote_code=trust_remote_code,
                                             dtype=torch.float16 if self.device == 'cuda' else torch.float32)
        try:
            self.model.to(self.device)
        except (RuntimeError, NotImplementedError):
            if self.device != 'mps':
                raise
            self._fallback_to_cpu()
        self.model.eval()

    def _fallback_to_cpu(self) -> None:
        logger.warning('Apple GPU could not run this model/batch; continuing on CPU', exc_info=True)
        self.device = 'cpu'
        self.model.to('cpu').float()
        torch.mps.empty_cache()

    def embed(self, texts: list[str], batch_size: int) -> np.ndarray:
        if batch_size <= 0:
            raise ValueError('batch_size must be positive')
        if not texts:
            return np.empty((0, self.truncate_dim), dtype=np.float32)
        vectors = []
        position = 0
        effective_batch = batch_size
        while position < len(texts):
            batch = texts[position:position + effective_batch]
            try:
                with torch.inference_mode():
                    encoded = self.model.encode(batch, task=self.task, max_length=self.max_length,
                                                truncate_dim=self.truncate_dim, device=self.device,
                                                batch_size=len(batch))
                if isinstance(encoded, torch.Tensor):
                    encoded = encoded.detach().cpu().numpy()
                array = np.asarray(encoded, dtype=np.float32)
                if array.shape != (len(batch), self.truncate_dim) or not np.isfinite(array).all():
                    raise ValueError('Embedding model returned invalid vectors')
                norms = np.linalg.norm(array, axis=1, keepdims=True)
                if np.any(norms < 1e-12):
                    raise ValueError('Embedding model returned an empty vector')
                vectors.append(array / norms)
                position += len(batch)
            except (RuntimeError, NotImplementedError) as error:
                out_of_memory = 'out of memory' in str(error).lower() or isinstance(error, torch.OutOfMemoryError)
                if out_of_memory and effective_batch > 1:
                    effective_batch = max(1, effective_batch // 2)
                    logger.warning('Embedding memory limit: retrying with batch size %s', effective_batch)
                    if self.device == 'mps':
                        torch.mps.empty_cache()
                    elif self.device == 'cuda':
                        torch.cuda.empty_cache()
                    continue
                if self.device == 'mps':
                    self._fallback_to_cpu()
                    continue
                raise
        return np.vstack(vectors)
