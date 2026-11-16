package main.newsmap.web;

import crawler.CrawlerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import crawler.CrawlerBuilder.CrawlResult;

@Component
@ConditionalOnProperty(name = "newsmap.pipeline.enabled", havingValue = "true", matchIfMissing = true)
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
        // Resume pending input on restart even if no new URLs can be fetched.
        runPipelineAsync("STARTUP");
    }

    @Scheduled(cron = "${newsmap.pipeline.cron:0 0 */2 * * *}", zone = "${newsmap.pipeline.zone:UTC}")
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
            ArticleBatchQueue queue = new ArticleBatchQueue(Path.of("data/articles"), Path.of("data/article-batches"));
            // Recover leftovers from an interrupted crawl before writing new input.
            queue.capture();
            Exception failure = null;
            try {
                CrawlerBuilder builder = new CrawlerBuilder(maxArticlesPerSource, true);
                crawlResult = builder.startCrawl();
            } catch (Exception crawlFailure) {
                failure = crawlFailure;
                System.err.println("[Pipeline] Crawl failed; processing retained input anyway: " + crawlFailure.getMessage());
            }
            queue.capture();
            var batches = queue.pending();
            int completed = 0;
            for (var batch : batches) {
                try {
                    processBatch(batch);
                    queue.complete(batch);
                    completed++;
                } catch (Exception batchFailure) {
                    failure = batchFailure;
                    System.err.println("[Pipeline] Retaining batch for retry: " + batch.directory() + ": " + batchFailure.getMessage());
                }
            }
            if (failure != null) throw failure;
            if (completed == 0) {
                pipelineRuns.finish(runId, "NO_CHANGES", crawlResult, eventSnapshot(), null);
                return;
            }

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

    private void processBatch(ArticleBatchQueue.Batch batch) throws Exception {
        if (!Files.exists(batch.embeddings())) {
            runPythonScript("embedding_service.cli.embed_articles",
                    "--config", "configs/default.yaml",
                    "--input-dir", batch.input().toAbsolutePath().toString(),
                    "--output-h5", batch.embeddings().toAbsolutePath().toString(),
                    "--batch-size", "4");
        } else {
            System.out.println("[Pipeline] Reusing completed embeddings: " + batch.embeddings());
        }
        runPythonScript("embedding_service.cli.match_events",
                "--input-h5", batch.embeddings().toAbsolutePath().toString(),
                "--state-db", "outputs/events.db", "--output-json", "outputs/events.json",
                "--threshold", "0.82", "--active-window-hours", "336");
        analysis.EventLocationEnricher.enrichAndSave();
        notifications.refreshAllUsers();
        // UMAP/HDBSCAN remains available through its CLI for offline quality analysis.
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
        pb.environment().remove("OPENROUTER_API_KEY");
        
        pb.redirectErrorStream(true);
        pb.inheritIO(); // forward stdout/stderr to main console

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Python module " + module + " exited with code " + exitCode);
        }
    }
}
