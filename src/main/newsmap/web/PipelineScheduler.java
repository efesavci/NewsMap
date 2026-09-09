package main.newsmap.web;

import crawler.CrawlerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import crawler.CrawlerBuilder.CrawlResult;

@Component
@EnableScheduling
public class PipelineScheduler {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final NotificationService notifications;
    private final PipelineRunStore pipelineRuns;
    private volatile boolean isRunning = false;

    @Value("${newsmap.pipeline.max-articles-per-source:100}")
    private int maxArticlesPerSource;

    public PipelineScheduler(NotificationService notifications, PipelineRunStore pipelineRuns) {
        this.notifications = notifications;
        this.pipelineRuns = pipelineRuns;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Path hotspotsPath = Path.of("data/hotspots.json");
        Path clustersPath = Path.of("embeddings-service/outputs/clusters.csv");
        Path eventsPath = Path.of("embeddings-service/outputs/events.json");

        if (Files.exists(eventsPath)) {
            executor.submit(() -> {
                try {
                    analysis.EventLocationEnricher.enrichAndSave();
                    notifications.refreshAllUsers();
                } catch (Exception e) {
                    System.err.println("[PipelineScheduler] Error locating existing events: " + e.getMessage());
                }
            });
        }

        boolean hotspotsNeedRefresh = !Files.exists(hotspotsPath) || !hasLocationConfidence(hotspotsPath);
        if (hotspotsNeedRefresh) {
            System.out.println("[PipelineScheduler] Hotspots are missing or use an older geotag format.");
            if (Files.exists(clustersPath)) {
                System.out.println("[PipelineScheduler] Found existing clusters.csv. Geotagging initial clusters in the background...");
                executor.submit(() -> {
                    try {
                        analysis.ClusterInspector.precomputeAndSaveHotspots();
                    } catch (Exception e) {
                        System.err.println("[PipelineScheduler] Error geocoding initial clusters on startup: " + e.getMessage());
                    }
                });
            } else {
                System.out.println("[PipelineScheduler] No clusters found. Running initial news gathering pipeline in the background...");
                runPipelineAsync("STARTUP");
            }
        } else {
            System.out.println("[PipelineScheduler] Existing precomputed hotspots found. Ready immediately. Background refresh uses the configured schedule.");
        }
    }

    private boolean hasLocationConfidence(Path hotspotsPath) {
        if (!Files.exists(hotspotsPath)) return false;
        try {
            return Files.readString(hotspotsPath).contains("\"locationConfidence\"");
        } catch (IOException e) {
            return false;
        }
    }

    @Scheduled(cron = "${newsmap.pipeline.cron:0 */15 * * * *}")
    public void runScheduled() {
        System.out.println("[PipelineScheduler] Running scheduled incremental news gathering pipeline...");
        runPipelineAsync("SCHEDULED");
    }

    public synchronized void runPipelineAsync() {
        runPipelineAsync("MANUAL");
    }

    public synchronized void runPipelineAsync(String trigger) {
        if (isRunning) {
            System.out.println("[PipelineScheduler] Pipeline is already running. Skipping.");
            return;
        }
        isRunning = true;
        executor.submit(() -> {
            try {
                runPipeline(trigger);
            } finally {
                isRunning = false;
            }
        });
    }

    private void runPipeline(String trigger) {
        PipelineRunStore.EventSnapshot before = eventSnapshot();
        String runId = pipelineRuns.start(trigger, before);
        CrawlResult crawlResult = null;
        try {
            System.out.println("[Pipeline] Step 1/6: Running Java Crawler...");
            // Clean old articles
            File articleDir = new File("data/articles");
            if (articleDir.exists()) {
                File[] files = articleDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
            
            CrawlerBuilder builder = new CrawlerBuilder(maxArticlesPerSource, true);
            crawlResult = builder.startCrawl();
            int newArticleCount = crawlResult.fetched();
            System.out.println("[Pipeline] Step 1/6: Java Crawler completed with " + newArticleCount + " new articles.");

            if (newArticleCount == 0) {
                System.out.println("[Pipeline] No new articles. Skipping embedding and event matching for this run.");
                pipelineRuns.finish(runId, "NO_CHANGES", crawlResult, eventSnapshot(), null);
                return;
            }

            // Activate virtual env and run Python embedding & clustering
            System.out.println("[Pipeline] Step 2/6: Generating Embeddings (Python)...");
            runPythonScript("embedding_service.cli.embed_articles", 
                    "--config", "configs/default.yaml", 
                    "--input-dir", "../data/articles", 
                    "--output-h5", "outputs/articles.h5", 
                    "--batch-size", "4");

            System.out.println("[Pipeline] Step 3/6: Matching Articles to Persistent Events...");
            runPythonScript("embedding_service.cli.match_events",
                    "--input-h5", "outputs/articles.h5",
                    "--state-db", "outputs/events.db",
                    "--output-json", "outputs/events.json",
                    "--threshold", "0.82",
                    "--active-window-hours", "336");

            System.out.println("[Pipeline] Step 4/6: Resolving Persistent Event Locations...");
            analysis.EventLocationEnricher.enrichAndSave();
            notifications.refreshAllUsers();

            // HDBSCAN is retained only as an offline quality comparison while
            // the current globe still consumes the legacy hotspot projection.
            System.out.println("[Pipeline] Step 5/6: Running Offline HDBSCAN Quality Pass...");
            runPythonScript("embedding_service.cli.cluster_articles", 
                    "--config", "configs/default.yaml", 
                    "--input-h5", "outputs/articles.h5", 
                    "--output-clusters", "outputs/clusters.csv", 
                    "--reducer", "umap", 
                    "--reducer-n-components", "12", 
                    "--reducer-n-neighbors", "10", 
                    "--reducer-min-dist", "0.0", 
                    "--reducer-metric", "cosine", 
                    "--clusterer", "hdbscan", 
                    "--hdbscan-min-cluster-size", "4", 
                    "--hdbscan-min-samples", "2",
                    "--hdbscan-cluster-selection-method", "leaf");

            System.out.println("[Pipeline] Step 6/6: Geotagging Legacy Globe Clusters...");
            analysis.ClusterInspector.precomputeAndSaveHotspots();

            System.out.println("[Pipeline] Pipeline execution finished successfully!");
            pipelineRuns.finish(runId, "SUCCEEDED", crawlResult, eventSnapshot(), null);
        } catch (Exception e) {
            System.err.println("[Pipeline] Error running pipeline: " + e.getMessage());
            e.printStackTrace();
            try {
                pipelineRuns.finish(runId, "FAILED", crawlResult, eventSnapshot(), e.getMessage());
            } catch (Exception recordError) {
                System.err.println("[Pipeline] Could not record failed run: " + recordError.getMessage());
            }
        }
    }

    private PipelineRunStore.EventSnapshot eventSnapshot() {
        try {
            var feed = EventController.readFeed();
            int articles = feed.events().stream().mapToInt(event -> event.articleCount()).sum();
            return new PipelineRunStore.EventSnapshot(feed.events().size(), articles);
        } catch (Exception ignored) {
            return new PipelineRunStore.EventSnapshot(0, 0);
        }
    }

    private void runPythonScript(String module, String... args) throws IOException, InterruptedException {
        String pythonBinary = "embeddings-service/venv/bin/python";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pythonBinary = "embeddings-service/venv/Scripts/python.exe";
        }
        
        File binFile = new File(pythonBinary);
        if (binFile.exists()) {
            pythonBinary = binFile.getAbsolutePath();
        } else {
            pythonBinary = "python3";
        }

        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(pythonBinary);
        command.add("-m");
        command.add(module);
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("embeddings-service"));
        
        // Add src to PYTHONPATH so python can locate the embedding_service package
        String pythonPath = new File("embeddings-service/src").getAbsolutePath();
        pb.environment().put("PYTHONPATH", pythonPath);
        
        pb.redirectErrorStream(true);
        pb.inheritIO(); // forward stdout/stderr to main console

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Python module " + module + " exited with code " + exitCode);
        }
    }
}
