# NewsMap 🌍📰

**Interactive world map for discovering the latest and most important news by region.**

NewsMap is a full-stack application that visualizes real-time news headlines on an interactive world map. Users can click on any region to explore trending stories, powered by a Java Spring Boot backend and a Python-based AI embeddings service for intelligent text mining and clustering.

## 🚀 Features

* **Interactive Map Interface**: Clickable world map to filter news by country or region.
* **Real-Time Headlines**: Fetches the latest news using open news APIs.
* **AI-Powered Insights**: Uses a dedicated Python service (`embeddings-service`) to analyze, cluster, and extract meaning from news text.
* **Region-Based Filtering**: Intuitively browse news specific to selected geographical areas.
* **Modern Tech Stack**: Built with Java Spring Boot for robust backend handling and Python for specialized ML tasks.

## 🛠️ Tech Stack

* **Backend**: Java
* **AI/ML Service**: Python (Embeddings, Text Mining)
* **Build Tool**: Maven
* **Frontend**: JavaFx (Interactive Map)
* **Data**: General-purpose website crawler written in Java

## 📂 Project Structure

* **`main.newsmap/`**: The main JavaFX application (Backend & Web Server).
* **`embeddings-service/`**: A Python microservice responsible for generating text embeddings and handling ML tasks.
* **`configs/newsConfigs/`**: Configuration files for news sources and crawling settings.

## ⚙️ Prerequisites

Before you begin, ensure you have the following installed:

* **Java 21** or higher
* **Python 3.9** or higher
* **Maven** (or use the included `mvnw` wrapper)
