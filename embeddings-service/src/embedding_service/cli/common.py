"""Common CLI helpers."""

from __future__ import annotations

from embedding_service.config import deep_get, load_config


def cfg(path: str | None) -> dict:
    return load_config(path).data


def pick(cli_value, conf: dict, dotted: str, default=None):
    return cli_value if cli_value is not None else deep_get(conf, dotted, default)
