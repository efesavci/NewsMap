import json
import numpy as np
from pathlib import Path
from transformers import AutoTokenizer, AutoModel
import torch
from nomic import atlas


# -------------------------------
# Load news articles from JSONL
# -------------------------------
def load_articles(base_path="../data/articles"):
    base = Path(base_path)
    articles = []   # store full metadata

    for jsonl_file in base.glob("*.jsonl"):
        print(f"[LOAD] {jsonl_file}")
        with open(jsonl_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                article = json.loads(line)

                # Ensure `body` exists
                if "body" in article and article["body"].strip():
                    articles.append(article)

    return articles


# -------------------------------
# Embed using nomic long-context model
# -------------------------------
def embed_articles(texts):
    print("[MODEL] Loading nomic-ai/nomic-embed-text-v1.5")

    tokenizer = AutoTokenizer.from_pretrained(
        "nomic-ai/nomic-embed-text-v1.5",
        trust_remote_code=True
    )
    model = AutoModel.from_pretrained(
        "nomic-ai/nomic-embed-text-v1.5",
        trust_remote_code=True
    )
    device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"[MODEL] Using device: {device}")

        model = model.to(device)

    vectors = []

    for text in texts:
        inputs = tokenizer(
            text,
            return_tensors="pt",
            truncation=True,
            max_length=8192   # long context supported
        ).to(device)
        with torch.no_grad():
            outputs = model(**inputs)

        # Official nomic pooling method (mean pooling)
        embedding = outputs.last_hidden_state.mean(dim=1).detach().cpu().numpy()[0]
        vectors.append(embedding)

    return np.array(vectors)


# -------------------------------
# Main execution (embedding + Nomic Atlas clustering)
# -------------------------------
def main():
    # 1. Load articles
    articles = load_articles()
    texts = [a["body"] for a in articles]
    ids = [a["id"] for a in articles]
    titles = [a.get("title", "") for a in articles]

    print(f"[INFO] Loaded {len(texts)} articles")

    # 2. Embed texts
    vectors = embed_articles(texts)
    print(f"[INFO] Embeddings shape: {vectors.shape}")

    # 3. Create IDs for each article (Atlas requires an ID field)
    ids = [f"doc_{i}" for i in range(len(texts))]

    # 4. Run Nomic Atlas clustering
    print("[ATLAS] Running clustering...")
    data_for_atlas = [
        {
            "id": ids[i],
            "title": titles[i],
            "embedding": vectors[i].tolist()
        }
        for i in range(len(ids))
    ]

    project = atlas.map_data(
        data=data_for_atlas,
        id_field="id",
        embedding_field="embedding"
    )

    # 5. Retrieve labels
    labels = project["embeddings"]["cluster"]

    print("[ATLAS] Clustering finished!")
    print("Cluster labels:", labels)
    for i in range(len(articles)):
        a = articles[i]
        cluster_output.append({
            "id": a["id"],
            "title": a.get("title", ""),
            "source": a.get("source", ""),
            "publishTime": a.get("publishTime", ""),
            "crawledAt": a.get("crawledAt", ""),
            "cluster": int(labels[i])
        })
    print("Cluster output:", cluster_output)
    with open("embeddings/cluster_results.json", "w", encoding="utf-8") as f:
        json.dump(cluster_output, f, indent=2, ensure_ascii=False)

    print("Saved → embeddings/cluster_results.json")
    print("[DONE] Pipeline completed successfully.")


if __name__ == "__main__":
    main()
