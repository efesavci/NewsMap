"""Pipeline for article ingestion + embedding artifact creation."""

from __future__ import annotations

import logging

from embedding_service.embedding.jina_embedder import JinaEmbedder
from embedding_service.io.articles import load_articles
from embedding_service.io.embeddings import save_embeddings_h5

logger = logging.getLogger(__name__)
MAX_BODY_CHARS = 2_000


def run_embed_pipeline(
    input_dir: str,
    output_h5: str,
    model_name: str,
    batch_size: int,
    max_length: int,
    trust_remote_code: bool,
    normalize_newlines: bool = True,
) -> dict:
    """Run end-to-end embedding generation.

    Robust replacement for old `main.py`, separating IO/model/storage concerns.
    """
    articles = load_articles(input_dir=input_dir, normalize_newlines=normalize_newlines)
    texts = [f"{article.title or ''}\n\n{article.body[:MAX_BODY_CHARS]}".strip() for article in articles]
    embedder = JinaEmbedder(model_name=model_name, trust_remote_code=trust_remote_code, max_length=max_length)
    vectors = embedder.embed(texts=texts, batch_size=batch_size)
    save_embeddings_h5(path=output_h5, articles=articles, vectors=vectors)
    logger.info("Embedded %s articles -> %s", len(articles), output_h5)
    return {"num_articles": len(articles), "embedding_dim": int(vectors.shape[1]), "output_h5": output_h5}
