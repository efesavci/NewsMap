import h5py
import numpy as np
import umap
import matplotlib.pyplot as plt
from tqdm import tqdm
import plotly.graph_objects as go

H5_PATH = "embeddings/articles.h5"


def load_embeddings(h5_path):
    print("[LOAD] Reading embeddings from HDF5…")

    vectors = []
    ids = []
    titles = []

    with h5py.File(h5_path, "r") as h5f:
        for dataset_id in tqdm(h5f.keys()):
            # Load embedding vector
            vec = np.array(h5f[dataset_id])
            vectors.append(vec)

            # Store dataset ID
            ids.append(dataset_id)

            # Load title attribute if exists, otherwise empty string
            title = h5f[dataset_id].attrs.get("title", "")
            titles.append(title)

    vectors = np.vstack(vectors)
    return vectors, ids, titles


def run_umap(vectors):
    print("[UMAP] Reducing to 3D…")

    reducer = umap.UMAP(
        n_components=3,
        n_neighbors=5,
        min_dist=0.01,
        metric="cosine",
        random_state=42,
    )

    embedding3d = reducer.fit_transform(vectors)
    return embedding3d


def plot_3d(points, labels=None):
    print("[PLOT] Rendering 3D UMAP scatter plot…")

    fig = plt.figure(figsize=(10, 7))
    ax = fig.add_subplot(111, projection="3d")

    ax.scatter(
        points[:, 0],
        points[:, 1],
        points[:, 2],
        c="blue",
        s=12,
        alpha=0.7
    )

    ax.set_title("UMAP 3D Projection of Embeddings", fontsize=14)
    ax.set_xlabel("UMAP-1")
    ax.set_ylabel("UMAP-2")
    ax.set_zlabel("UMAP-3")

    plt.show()



def plot_3d_interactive(points, ids, titles):
    # Combine IDs and titles for hover text
    hover_texts = [
        f"ID: {id}<br>Title: {title}"
        for id, title in zip(ids, titles)
    ]

    fig = go.Figure(
        data=[go.Scatter3d(
            x=points[:, 0],
            y=points[:, 1],
            z=points[:, 2],
            mode="markers",
            marker=dict(
                size=3,
                opacity=0.7,
            ),
            text=hover_texts,
            hovertemplate="%{text}<extra></extra>"
        )]
    )

    fig.update_layout(
        title="UMAP 3D Embeddings (with Titles)",
        scene=dict(
            xaxis_title="UMAP-1",
            yaxis_title="UMAP-2",
            zaxis_title="UMAP-3",
        ),
        width=1000,
        height=800
    )

    fig.show()


def main():
    vectors, ids, titles = load_embeddings("embeddings/articles.h5")
    points3d = run_umap(vectors)
    plot_3d_interactive(points3d, ids, titles)


if __name__ == "__main__":
    main()
