"""CLI for persistent article-to-event matching."""

from __future__ import annotations

import argparse
import json

from embedding_service.logging_utils import setup_logging
from embedding_service.pipelines.event_pipeline import run_event_pipeline


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Assign embedded articles to persistent news events")
    parser.add_argument("--input-h5", default="outputs/articles.h5")
    parser.add_argument("--state-db", default="outputs/events.db")
    parser.add_argument("--output-json", default="outputs/events.json")
    parser.add_argument("--threshold", type=float, default=0.82)
    parser.add_argument("--active-window-hours", type=int, default=336)
    parser.add_argument("--log-level", default="INFO")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    setup_logging(args.log_level)
    result = run_event_pipeline(
        input_h5=args.input_h5,
        state_db=args.state_db,
        output_json=args.output_json,
        threshold=args.threshold,
        active_window_hours=args.active_window_hours,
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
