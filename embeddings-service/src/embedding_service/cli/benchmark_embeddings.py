"""Measure local embedding throughput without changing the event store or crawl ledger."""
from __future__ import annotations

import argparse
import json
import platform
import resource
import time
from pathlib import Path

from embedding_service.embedding.jina_embedder import JinaEmbedder
from embedding_service.io.articles import load_articles
from embedding_service.pipelines.embed_pipeline import MAX_BODY_CHARS
from embedding_service.logging_utils import setup_logging


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-dir', required=True)
    parser.add_argument('--device', choices=['auto', 'cpu', 'cuda', 'mps'], default='auto')
    parser.add_argument('--limit', type=int, default=32)
    parser.add_argument('--repeat', type=int, default=1)
    parser.add_argument('--batch-size', type=int, default=4)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if min(args.limit, args.repeat, args.batch_size) < 1: parser.error('Counts must be positive')
    setup_logging('INFO')
    articles = load_articles(args.input_dir)[:args.limit]
    texts = [f'{a.title or ""}\n\n{a.body[:MAX_BODY_CHARS]}'.strip() for a in articles] * args.repeat
    start = time.perf_counter()
    model = JinaEmbedder('jinaai/jina-embeddings-v3', max_length=512, device=args.device)
    loaded = time.perf_counter() - start
    model.embed(texts[:1], 1)  # Warm-up includes initial GPU compilation/fallback.
    start = time.perf_counter()
    vectors = model.embed(texts, args.batch_size)
    seconds = time.perf_counter() - start
    rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    report = dict(device=model.device, uniqueArticles=len(articles), measuredInputs=len(texts),
                  batchSize=args.batch_size, dimensions=vectors.shape[1], modelLoadSeconds=round(loaded, 3),
                  embeddingSeconds=round(seconds, 3), inputsPerSecond=round(len(texts)/seconds, 3),
                  peakProcessMemoryMiB=round(rss/(1024**2 if platform.system() == 'Darwin' else 1024), 1),
                  note='Small local sample; repeated inputs and warm-cache timings are not a production capacity guarantee.')
    if args.output: args.output.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__': main()
