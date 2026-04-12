"""3D interactive embedding visualization."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import plotly.graph_objects as go


def plot_embeddings_3d(
    points: np.ndarray,
    metadata: list[dict],
    output_html: str,
    color_by: str = "source",
    marker_size: int = 4,
    opacity: float = 0.75,
    title: str = "UMAP 3D Embeddings",
) -> None:
    """Save an interactive 3D scatter plot to HTML."""
    colors = [str(item.get(color_by, "unknown")) for item in metadata]
    hover_texts = [
        f"id={item.get('article_id', '')}<br>title={item.get('title', '')}<br>{color_by}={item.get(color_by, '')}"
        for item in metadata
    ]

    fig = go.Figure(
        data=[
            go.Scatter3d(
                x=points[:, 0],
                y=points[:, 1],
                z=points[:, 2],
                mode="markers",
                marker={"size": marker_size, "opacity": opacity, "color": colors},
                text=hover_texts,
                hovertemplate="%{text}<extra></extra>",
            )
        ]
    )
    fig.update_layout(title=title, scene={"xaxis_title": "X", "yaxis_title": "Y", "zaxis_title": "Z"})
    target = Path(output_html)
    target.parent.mkdir(parents=True, exist_ok=True)
    fig.write_html(str(target))
