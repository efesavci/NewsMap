"""HDF5 embedding artifact read/write layer."""

from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from embedding_service.models import Article


def save_embeddings_h5(path: str, articles: list[Article], vectors: np.ndarray) -> None:
    """Persist embedding vectors and scalar metadata to HDF5.

    Body text is intentionally excluded for smaller artifacts and privacy.
    """
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(dir=output.parent, suffix=".h5.tmp")
    os.close(descriptor)
    temporary = Path(name)
    try:
        with h5py.File(temporary, "w") as h5f:
            for article, vector in zip(articles, vectors, strict=True):
                embedding_id = article.article_id or "unknown"
                dataset = h5f.create_dataset(embedding_id, data=vector.astype("float32"), dtype="float32")
                if article.article_id:
                    dataset.attrs["article_id"] = article.article_id
                if article.title is not None:
                    dataset.attrs["title"] = article.title
                if article.source is not None:
                    dataset.attrs["source"] = article.source
                for key, value in article.metadata.items():
                    if isinstance(value, (str, int, float, bool)):
                        dataset.attrs[key] = value
                    else:
                        dataset.attrs[key] = json.dumps(value)
        with temporary.open("rb") as handle:
            os.fsync(handle.fileno())
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)


def load_embeddings_h5(path: str) -> tuple[np.ndarray, list[str], list[dict[str, Any]]]:
    """Load all vectors, ids, and metadata attributes from an HDF5 artifact."""
    h5_path = Path(path)
    if not h5_path.exists():
        raise FileNotFoundError(f"Embedding HDF5 not found: {path}")

    vectors: list[np.ndarray] = []
    ids: list[str] = []
    metadata: list[dict[str, Any]] = []
    with h5py.File(h5_path, "r") as h5f:
        for embedding_id in h5f.keys():
            dset = h5f[embedding_id]
            vectors.append(np.array(dset))
            ids.append(embedding_id)
            metadata.append({k: dset.attrs[k].item() if hasattr(dset.attrs[k], "item") else dset.attrs[k] for k in dset.attrs.keys()})

    if not vectors:
        raise ValueError(f"No embeddings found in: {path}")
    return np.vstack(vectors), ids, metadata
