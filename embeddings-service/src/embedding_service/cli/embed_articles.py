"""CLI for article embedding pipeline."""

from __future__ import annotations

import argparse
import json

from embedding_service.cli.common import cfg, pick
from embedding_service.logging_utils import setup_logging
from embedding_service.pipelines.embed_pipeline import run_embed_pipeline


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Embed JSONL articles and persist embeddings in HDF5")
    parser.add_argument("--config", type=str, default=None)
    parser.add_argument("--input-dir", type=str, default=None)
    parser.add_argument("--output-h5", type=str, default=None)
    parser.add_argument("--model-name", type=str, default=None)
    parser.add_argument("--batch-size", type=int, default=None)
    parser.add_argument("--max-length", type=int, default=None)
    parser.add_argument("--log-level", type=str, default=None)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    conf = cfg(args.config)
    setup_logging(pick(args.log_level, conf, "logging.level", "INFO"))

    result = run_embed_pipeline(
        input_dir=pick(args.input_dir, conf, "paths.input_dir", "data/articles"),
        output_h5=pick(args.output_h5, conf, "paths.output_h5", "outputs/articles.h5"),
        model_name=pick(args.model_name, conf, "embedding.model_name", "jinaai/jina-embeddings-v3"),
        batch_size=int(pick(args.batch_size, conf, "embedding.batch_size", 4)),
        max_length=int(pick(args.max_length, conf, "embedding.max_length", 4096)),
        trust_remote_code=bool(pick(None, conf, "embedding.trust_remote_code", True)),
        normalize_newlines=bool(pick(None, conf, "embedding.normalize_newlines", True)),
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
