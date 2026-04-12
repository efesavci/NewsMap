"""CLI for 3D embedding visualization."""

from __future__ import annotations

import argparse
import json

from embedding_service.cli.common import cfg, pick
from embedding_service.logging_utils import setup_logging
from embedding_service.pipelines.visualize_pipeline import run_visualize_pipeline


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Reduce embeddings for visualization and render 3D HTML")
    p.add_argument("--config", type=str, default=None)
    p.add_argument("--input-h5", type=str, default=None)
    p.add_argument("--output-html", type=str, default=None)
    p.add_argument("--vis-reducer", type=str, default=None)
    p.add_argument("--vis-n-components", type=int, default=None)
    p.add_argument("--vis-n-neighbors", type=int, default=None)
    p.add_argument("--vis-min-dist", type=float, default=None)
    p.add_argument("--vis-metric", type=str, default=None)
    p.add_argument("--vis-random-state", type=int, default=None)
    p.add_argument("--color-by", type=str, default=None)
    p.add_argument("--marker-size", type=int, default=None)
    p.add_argument("--opacity", type=float, default=None)
    p.add_argument("--title", type=str, default=None)
    p.add_argument("--log-level", type=str, default=None)
    return p


def main() -> None:
    args = build_parser().parse_args()
    conf = cfg(args.config)
    setup_logging(pick(args.log_level, conf, "logging.level", "INFO"))

    reducer_params = {
        "n_components": int(pick(args.vis_n_components, conf, "visualization_reduction.n_components", 3)),
        "n_neighbors": int(pick(args.vis_n_neighbors, conf, "visualization_reduction.n_neighbors", 15)),
        "min_dist": float(pick(args.vis_min_dist, conf, "visualization_reduction.min_dist", 0.1)),
        "metric": pick(args.vis_metric, conf, "visualization_reduction.metric", "cosine"),
        "random_state": pick(args.vis_random_state, conf, "visualization_reduction.random_state", 42),
    }

    result = run_visualize_pipeline(
        input_h5=pick(args.input_h5, conf, "paths.output_h5", "outputs/articles.h5"),
        output_html=pick(args.output_html, conf, "paths.visualization_html", "outputs/umap_3d.html"),
        reducer_method=pick(args.vis_reducer, conf, "visualization_reduction.method", "umap"),
        reducer_params=reducer_params,
        color_by=pick(args.color_by, conf, "visualization.color_by", "source"),
        marker_size=int(pick(args.marker_size, conf, "visualization.marker_size", 4)),
        opacity=float(pick(args.opacity, conf, "visualization.opacity", 0.75)),
        title=pick(args.title, conf, "visualization.title", "UMAP 3D Embeddings"),
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
