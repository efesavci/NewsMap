import json
import hashlib
from pathlib import Path

import numpy as np
import h5py
import torch
from tqdm import tqdm
from transformers import AutoTokenizer, AutoModel


# -----------------------------------------------------
# Load articles from JSONL files
# -----------------------------------------------------
def load_articles(base_path="../data/articles"):
    base = Path(base_path)
    articles = []

    for jsonl_file in base.glob("*.jsonl"):
        print(f"[LOAD] {jsonl_file}")
        with open(jsonl_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue

                article = json.loads(line)

                if "body" in article and article["body"].strip():
                    # ✅ Remove newlines from body
                    article["body"] = article["body"].replace("\n", " ").strip()
                    articles.append(article)

    return articles


# -----------------------------------------------------
# Embed using Jina v3
# -----------------------------------------------------
def embed_articles(texts, batch_size=8):
    print("[MODEL] Loading jinaai/jina-embeddings-v3")

    tokenizer = AutoTokenizer.from_pretrained(
        "jinaai/jina-embeddings-v3",
        trust_remote_code=True
    )

    model = AutoModel.from_pretrained(
        "jinaai/jina-embeddings-v3",
        trust_remote_code=True,
        dtype=torch.float16 if torch.cuda.is_available() else torch.float32
    )

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"[MODEL] Using device: {device}")
    model.to(device)
    model.eval()

    vectors = []

    print(f"[EMBED] Encoding {len(texts)} texts in batches of {batch_size}...")

    for i in tqdm(range(0, len(texts), batch_size)):
        batch = texts[i:i + batch_size]

        inputs = tokenizer(
            batch,
            return_tensors="pt",
            truncation=True,
            padding=True,
            max_length=8192
        ).to(device)

        with torch.no_grad():
            outputs = model(**inputs)

        last_hidden = outputs.last_hidden_state
        batch_embs = last_hidden.mean(dim=1)

        vectors.append(batch_embs.cpu().numpy())

    return np.vstack(vectors)


# -----------------------------------------------------
# Save to HDF5
# -----------------------------------------------------
def save_to_h5(articles, embeddings, output_path="embeddings/articles.h5"):
    print("[SAVE] Saving to HDF5:", output_path)
    Path(output_path).parent.mkdir(exist_ok=True)

    with h5py.File(output_path, "w") as h5f:
        for article, vector in tqdm(zip(articles, embeddings), total=len(articles)):

            # Use article ID or cleaned body for hashing
            raw_id = article.get("id", article["body"])  # body already cleaned above

            # Dataset ID is sha256 hash
            dataset_id = hashlib.sha256(raw_id.encode("utf-8")).hexdigest()

            # Create dataset
            dset = h5f.create_dataset(dataset_id, data=vector, dtype="float32")

            # Save metadata EXCEPT 'body'
            for key, value in article.items():
                if key == "body":
                    continue  # DO NOT save body

                if isinstance(value, (str, int, float)):
                    dset.attrs[key] = value
                else:
                    dset.attrs[key] = json.dumps(value)

    print("[SAVE] Done!")


# -----------------------------------------------------
# Main
# -----------------------------------------------------
def main():
    print(">>> main.py STARTED")

    articles = load_articles()
    texts = [a["body"] for a in articles]  # already cleaned
    print(f"[INFO] Loaded {len(texts)} articles")

    embeddings = embed_articles(texts, batch_size=8)
    print(f"[INFO] Embeddings shape: {embeddings.shape}")

    save_to_h5(articles, embeddings)


if __name__ == "__main__":
    main()
