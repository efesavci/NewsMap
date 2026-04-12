"""Logging utilities for embedding service."""

from __future__ import annotations

import logging


def setup_logging(level: str = "INFO") -> None:
    """Configure process-wide logging with a consistent format."""
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
    )
