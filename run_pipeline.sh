#!/bin/bash
set -e

echo "======================================"
echo "🚀 Starting NewsMap Data Pipeline..."
echo "======================================"

# 1. Run Java Crawler
echo ""
echo "📰 [1/4] Running Crawler..."
echo "Cleaning old articles..."
rm -rf data/articles/*
./mvnw clean compile
./mvnw exec:java -Dexec.mainClass="crawler.CrawlerTester" -Dexec.classpathScope=compile

# 2. Setup Python Environment (if needed)
echo ""
echo "🐍 Setting up Python environment..."
cd embeddings-service
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi
source venv/bin/activate
pip install -r requirements.txt > /dev/null

# 3. Run Embeddings Pipeline
echo ""
echo "🧠 [2/4] Generating Embeddings..."
python -m embedding_service.cli.embed_articles \
  --config configs/default.yaml \
  --input-dir ../data/articles \
  --output-h5 outputs/articles.h5 \
  --batch-size 4

# 4. Match articles to persistent events
echo ""
echo "🧭 [3/4] Matching Persistent Events..."
python -m embedding_service.cli.match_events \
  --input-h5 outputs/articles.h5 \
  --state-db outputs/events.db \
  --output-json outputs/events.json \
  --threshold 0.82 \
  --active-window-hours 336

# 5. Run legacy HDBSCAN as an offline quality comparison
echo ""
echo "🧩 [4/4] Running Offline HDBSCAN Quality Pass..."
python -m embedding_service.cli.cluster_articles \
  --config configs/default.yaml \
  --input-h5 outputs/articles.h5 \
  --output-clusters outputs/clusters.csv \
  --reducer umap \
  --reducer-n-components 12 \
  --reducer-n-neighbors 10 \
  --reducer-min-dist 0.0 \
  --reducer-metric cosine \
  --clusterer hdbscan \
  --hdbscan-min-cluster-size 4 \
  --hdbscan-min-samples 2 \
  --hdbscan-cluster-selection-method leaf

echo ""
echo "✅ Pipeline complete! Persistent events saved to embeddings-service/outputs/events.json"
