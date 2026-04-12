"""Pipeline for reusable visualization reduction + rendering."""

from __future__ import annotations

from embedding_service.io.embeddings import load_embeddings_h5
from embedding_service.reduction.umap_reducer import UmapReducer
from embedding_service.visualization.plot_umap_3d import plot_embeddings_3d


def run_visualize_pipeline(
    input_h5: str,
    output_html: str,
    reducer_method: str,
    reducer_params: dict,
    color_by: str,
    marker_size: int,
    opacity: float,
    title: str,
) -> dict:
    """Run visualization-focused reduction and create 3D HTML plot.

    Robust replacement for old `dim_reducer.py` with configurable, reusable components.
    """
    vectors, _, metadata = load_embeddings_h5(input_h5)
    if reducer_method != "umap":
        raise ValueError(f"Unsupported visualization reducer: {reducer_method}")
    points = UmapReducer(**reducer_params).fit_transform(vectors)
    if points.shape[1] != 3:
        raise ValueError("Visualization requires 3 components for 3D plotting")
    plot_embeddings_3d(points, metadata, output_html, color_by=color_by, marker_size=marker_size, opacity=opacity, title=title)
    return {"output_html": output_html, "num_points": int(points.shape[0])}
