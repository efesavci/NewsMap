"""CLI for clustering embeddings."""

from __future__ import annotations

import argparse
import json

from embedding_service.cli.common import cfg, pick
from embedding_service.logging_utils import setup_logging
from embedding_service.pipelines.cluster_pipeline import run_cluster_pipeline


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Cluster embeddings from HDF5")
    p.add_argument("--config", type=str, default=None)
    p.add_argument("--input-h5", type=str, default=None)
    p.add_argument("--output-clusters", type=str, default=None)
    p.add_argument("--output-run-metadata", type=str, default=None)
    p.add_argument("--output-format", choices=["csv", "parquet"], default=None)
    p.add_argument("--reducer", type=str, default=None)
    p.add_argument("--reducer-n-components", type=int, default=None)
    p.add_argument("--reducer-n-neighbors", type=int, default=None)
    p.add_argument("--reducer-min-dist", type=float, default=None)
    p.add_argument("--reducer-metric", type=str, default=None)
    p.add_argument("--reducer-random-state", type=int, default=None)
    p.add_argument("--clusterer", type=str, choices=["dbscan", "hdbscan"], default=None)
    p.add_argument("--dbscan-eps", type=float, default=None)
    p.add_argument("--dbscan-min-samples", type=int, default=None)
    p.add_argument("--dbscan-metric", type=str, default=None)
    p.add_argument("--hdbscan-min-cluster-size", type=int, default=None)
    p.add_argument("--hdbscan-min-samples", type=int, default=None)
    p.add_argument("--hdbscan-metric", type=str, default=None)
    p.add_argument("--log-level", type=str, default=None)
    return p


def main() -> None:
    args = build_parser().parse_args()
    conf = cfg(args.config)
    setup_logging(pick(args.log_level, conf, "logging.level", "INFO"))

    reducer_params = {
        "n_components": int(pick(args.reducer_n_components, conf, "reduction.n_components", 12)),
        "n_neighbors": int(pick(args.reducer_n_neighbors, conf, "reduction.n_neighbors", 15)),
        "min_dist": float(pick(args.reducer_min_dist, conf, "reduction.min_dist", 0.1)),
        "metric": pick(args.reducer_metric, conf, "reduction.metric", "cosine"),
        "random_state": pick(args.reducer_random_state, conf, "reduction.random_state", 42),
    }
    result = run_cluster_pipeline(
        input_h5=pick(args.input_h5, conf, "paths.output_h5", "outputs/articles.h5"),
        output_clusters=pick(args.output_clusters, conf, "paths.clusters_output", "outputs/clusters.csv"),
        output_run_metadata=pick(args.output_run_metadata, conf, "paths.run_metadata_output", "outputs/run_metadata.json"),
        output_format=pick(args.output_format, conf, "paths.clusters_output_format", "csv"),
        reducer_method=pick(args.reducer, conf, "reduction.method", "umap"),
        reducer_params=reducer_params,
        clusterer_method=pick(args.clusterer, conf, "clustering.method", "hdbscan"),
        dbscan_params={
            "eps": float(pick(args.dbscan_eps, conf, "clustering.dbscan.eps", 0.5)),
            "min_samples": int(pick(args.dbscan_min_samples, conf, "clustering.dbscan.min_samples", 5)),
            "metric": pick(args.dbscan_metric, conf, "clustering.dbscan.metric", "euclidean"),
        },
        hdbscan_params={
            "min_cluster_size": int(pick(args.hdbscan_min_cluster_size, conf, "clustering.hdbscan.min_cluster_size", 12)),
            "min_samples": int(pick(args.hdbscan_min_samples, conf, "clustering.hdbscan.min_samples", 5)),
            "metric": pick(args.hdbscan_metric, conf, "clustering.hdbscan.metric", "euclidean"),
        },
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
