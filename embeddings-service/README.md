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
