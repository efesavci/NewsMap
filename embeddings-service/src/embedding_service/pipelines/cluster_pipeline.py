"""Pipeline for clustering embedding artifacts."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

from embedding_service.clustering.dbscan_clusterer import DbscanClusterer
from embedding_service.clustering.hdbscan_clusterer import HdbscanClusterer
from embedding_service.io.embeddings import load_embeddings_h5
from embedding_service.reduction.umap_reducer import UmapReducer
from embedding_service.scoring.cluster_metrics import compute_cluster_metrics


def run_cluster_pipeline(
    input_h5: str,
    output_clusters: str,
    output_run_metadata: str,
    reducer_method: str,
    reducer_params: dict,
    clusterer_method: str,
    dbscan_params: dict,
    hdbscan_params: dict,
    output_format: str = "csv",
) -> dict:
    """Run clustering in reduced space and export assignments.

    Robust replacement for old `cluster_reducer.py` where CLI args were parsed but ignored.
    """
    vectors, ids, metadata = load_embeddings_h5(input_h5)

    if reducer_method != "umap":
        raise ValueError(f"Unsupported reducer: {reducer_method}")
    reduced = UmapReducer(**reducer_params).fit_transform(vectors)

    probabilities = None
    if clusterer_method == "dbscan":
        labels = DbscanClusterer(**dbscan_params).fit_predict(reduced)
    elif clusterer_method == "hdbscan":
        clusterer = HdbscanClusterer(**hdbscan_params)
        labels = clusterer.fit_predict(reduced)
        probabilities = clusterer.probabilities
    else:
        raise ValueError(f"Unsupported clusterer: {clusterer_method}")

    rows = []
    for index, (article_id, label, attrs) in enumerate(zip(ids, labels.tolist(), metadata, strict=True)):
        row = {"embedding_id": article_id, "cluster_label": int(label), **attrs}
        if probabilities is not None:
            row["cluster_probability"] = float(probabilities[index])
        rows.append(row)
    df = pd.DataFrame(rows)

    out_path = Path(output_clusters)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if output_format == "csv":
        df.to_csv(out_path, index=False)
    elif output_format == "parquet":
        df.to_parquet(out_path, index=False)
    else:
        raise ValueError("output_format must be csv or parquet")

    metrics = compute_cluster_metrics(reduced, labels)
    run_metadata = {
        "input_h5": input_h5,
        "reducer_method": reducer_method,
        "reducer_params": reducer_params,
        "clusterer_method": clusterer_method,
        "dbscan_params": dbscan_params,
        "hdbscan_params": hdbscan_params,
        "metrics": metrics,
    }
    meta_path = Path(output_run_metadata)
    meta_path.parent.mkdir(parents=True, exist_ok=True)
    meta_path.write_text(json.dumps(run_metadata, indent=2), encoding="utf-8")
    return run_metadata
