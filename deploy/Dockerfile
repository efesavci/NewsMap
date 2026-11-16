FROM maven:3.9-eclipse-temurin-21 AS java-build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY resources ./resources
COPY tests ./tests
RUN mvn -B package spring-boot:repackage

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv ca-certificates curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN useradd --uid 10001 --create-home newsmap && mkdir -p embeddings-service/outputs data model-cache && chown -R newsmap:newsmap /app
COPY --chown=newsmap:newsmap embeddings-service/requirements.txt /app/embeddings-service/requirements.txt
USER newsmap
RUN python3 -m venv /app/embeddings-service/venv && /app/embeddings-service/venv/bin/pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu && /app/embeddings-service/venv/bin/pip install --no-cache-dir -r /app/embeddings-service/requirements.txt
COPY --chown=newsmap:newsmap embeddings-service/src /app/embeddings-service/src
COPY --chown=newsmap:newsmap embeddings-service/tests /app/embeddings-service/tests
RUN PYTHONPATH=/app/embeddings-service/src /app/embeddings-service/venv/bin/python -m unittest discover -s /app/embeddings-service/tests -q
COPY --chown=newsmap:newsmap embeddings-service/configs /app/embeddings-service/configs
COPY --chown=newsmap:newsmap configs /app/configs
COPY --chown=newsmap:newsmap resources /app/resources
COPY --from=java-build /build/target/newsmap-1.0-SNAPSHOT.jar /app/newsmap.jar
ENV HF_HOME=/app/model-cache PYTHONUNBUFFERED=1 NEWSMAP_SECURE_COOKIES=true
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl --fail --silent http://127.0.0.1:8080/api/health || exit 1
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/newsmap.jar"]
