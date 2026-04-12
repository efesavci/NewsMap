"""Configuration loading and validation."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(slots=True)
class AppConfig:
    """Container for merged application configuration."""

    data: dict[str, Any]

    def get(self, key: str, default: Any = None) -> Any:
        return self.data.get(key, default)


def load_config(config_path: str | None = None) -> AppConfig:
    """Load optional YAML/JSON configuration file."""
    if not config_path:
        return AppConfig(data={})

    path = Path(config_path)
    if not path.exists():
        raise FileNotFoundError(f"Config file not found: {path}")

    if path.suffix.lower() in {".yaml", ".yml"}:
        with path.open("r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
    elif path.suffix.lower() == ".json":
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
    else:
        raise ValueError("Config must be YAML (.yml/.yaml) or JSON (.json)")

    if not isinstance(data, dict):
        raise ValueError("Config root must be an object/dictionary")
    return AppConfig(data=data)


def deep_get(config: dict[str, Any], dotted_path: str, default: Any = None) -> Any:
    """Get nested config values by dotted path."""
    current: Any = config
    for part in dotted_path.split("."):
        if not isinstance(current, dict) or part not in current:
            return default
        current = current[part]
    return current
