"""Article ingestion utilities."""

from __future__ import annotations

import hashlib
import json
import logging
from pathlib import Path

from embedding_service.models import Article


def _stable_article_id(raw: dict) -> str:
    source_id = raw.get("id") or raw.get("article_id") or raw.get("hash_id") or raw.get("body", "")
    return hashlib.sha256(str(source_id).encode("utf-8")).hexdigest()


def load_articles(input_dir: str, normalize_newlines: bool = True) -> list[Article]:
    """Load crawled JSONL articles from a directory.

    Keeps scalar metadata and intentionally keeps body only in memory.
    """
    base = Path(input_dir)
    if not base.exists() or not base.is_dir():
        raise FileNotFoundError(f"Input article directory does not exist: {input_dir}")

    articles_by_id: dict[str, Article] = {}
    for file_path in sorted(base.glob("*.jsonl")):
        with file_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                if not line.strip():
                    continue
                try:
                    raw = json.loads(line)
                except json.JSONDecodeError:
                    if not line.endswith('\n'):
                        logging.getLogger(__name__).warning('Ignoring incomplete trailing record in %s; its URL remains retryable', file_path)
                        continue
                    raise
                body = str(raw.get("body", "")).strip()
                if not body:
                    continue
                if normalize_newlines:
                    body = body.replace("\n", " ").strip()
                metadata = {k: v for k, v in raw.items() if k not in {"body", "id", "article_id", "hash_id", "title", "source"}}
                metadata["excerpt"] = body[:800]
                article = Article(
                    body=body,
                    article_id=raw.get("article_id") or raw.get("hash_id") or raw.get("id") or _stable_article_id(raw),
                    title=raw.get("title"),
                    source=raw.get("source"),
                    metadata=metadata,
                )
                articles_by_id[article.article_id] = article
    articles = list(articles_by_id.values())
    if not articles:
        raise ValueError(f"No valid articles with non-empty body found in: {input_dir}")
    return articles
