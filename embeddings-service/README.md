# Embedding Service

A modular, configurable pipeline for article embedding, reduction, clustering, scoring, and visualization.

## Project Structure

```text
embeddings-service/
  pyproject.toml
  requirements.txt
  README.md
  configs/
    default.yaml
  data/
  outputs/
  src/
    embedding_service/
      config.py
      logging_utils.py
      models.py
      io/
      embedding/
      reduction/
      clustering/
      scoring/
      visualization/
      pipelines/
      cli/
```

## Why this refactor

- Replaces hardcoded paths/params with CLI + YAML/JSON config.
- Splits old mixed scripts into reusable modules.
- Unifies reducer (`fit_transform`) and clusterer (`fit_predict`) interfaces.
- Keeps clustering reduction and visualization reduction separate, but both use the same reusable reducer abstraction.
- Stores scalar metadata with embeddings and intentionally excludes article body from HDF5 artifacts.

## Install

```bash
pip install -r requirements.txt
pip install -e .
```

## Run

```bash
python -m embedding_service.cli.embed_articles \
  --config configs/default.yaml \
  --input-dir data/articles \
  --output-h5 outputs/articles.h5 \
  --batch-size 4
```

```bash
python -m embedding_service.cli.cluster_articles \
  --config configs/default.yaml \
  --input-h5 outputs/articles.h5 \
  --output-clusters outputs/clusters.csv \
  --reducer umap \
  --reducer-n-components 12 \
  --reducer-n-neighbors 15 \
  --reducer-min-dist 0.1 \
  --reducer-metric cosine \
  --clusterer hdbscan \
  --hdbscan-min-cluster-size 12 \
  --hdbscan-min-samples 5
```

```bash
python -m embedding_service.cli.visualize_embeddings \
  --config configs/default.yaml \
  --input-h5 outputs/articles.h5 \
  --output-html outputs/umap_3d.html \
  --vis-reducer umap \
  --vis-n-components 3 \
  --vis-n-neighbors 15 \
  --vis-min-dist 0.1 \
  --vis-metric cosine \
  --color-by source
```

## Notes

- `cluster_articles` exports cluster assignments to CSV or Parquet.
- `cluster_articles` also exports run metadata/parameters and clustering metrics as JSON.
- The module boundaries are designed for future server/API integration.

## Incremental processing and native Mac acceleration

The scheduled backend stores unfinished work in `data/article-batches/`.
Each batch retains its input until matching, location enrichment and notification
refresh succeed. Embeddings are published atomically as a batch checkpoint;
retries after matching/enrichment failures reuse that file. A crash before an
embedding checkpoint completes may recompute that batch. Matching checks stored
article IDs to prevent duplicate reports and updates.

The embedder selects CUDA, then Apple MPS, then CPU. Native macOS can use MPS;
the Linux Docker package uses CPU unless deployed with GPU support. Override
with `NEWSMAP_EMBEDDING_DEVICE=cpu` or the embedding CLI's `--device` argument.
Unsupported MPS operations retry on CPU. Memory errors reduce the batch size;
unrecoverable failures leave the input batch pending.

Event vectors are loaded into an in-memory index once per batch, then refreshed
as events change. A vectorized cosine/time filter shortlists events before the
existing lexical/entity checks. All candidates meeting the mandatory semantic
floor remain eligible; this is not an approximate top-k lookup. Vector comparison
still scales linearly with eligible event count, but avoids per-article full SQL
reloads and Python-level detailed scoring of unrelated events. The matching
horizon stays 14 days; the map's 48-hour window and saved libraries are independent.

UMAP/HDBSCAN remains available for offline analysis using the commands above.
It no longer runs automatically every two hours. Its input HDF5 should be supplied
explicitly from an analysis dataset, not from an acknowledged queue batch.

To benchmark without changing the crawl ledger or event database, from this directory:

```sh
HF_HUB_OFFLINE=1 PYTHONPATH=src venv/bin/python -m embedding_service.cli.benchmark_embeddings \
  --input-dir ../data/articles --limit 32 --device auto
```

Offline mode requires the model to be cached already. Use an input directory with
representative JSONL articles, including bodies. The report separates model load
and warm embedding time; a small or repeated sample does not establish capacity.

Validation on 5 November 2026: 14 Python tests passed. A native MPS smoke benchmark
on two existing articles repeated eight times produced 16 vectors in 4.892 seconds
after warm-up, at batch size 4 and 512 output dimensions. Model load took 2.774
seconds; reported peak process RSS was about 2,952 MiB (not total GPU/system memory).
The cached tokenizer emitted a compatibility warning; no tokenizer/model revisions
were changed in this work. These measurements exclude crawling, matching, location
enrichment and the server's concurrent workload.

A CPU/MPS comparison of the two sample articles had minimum cosine similarity
1.0 at displayed precision. A separate read-only matching check against 335 stored
events used 100 queries: full scan 0.103 seconds, in-memory shortlist including
index construction 0.005 seconds; detailed comparisons fell from 33,500 to 825
with identical event/acceptance decisions. This excludes database reload costs and
is a small local workload, not a guarantee for larger traffic.
