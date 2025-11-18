package crawler;

import global.Constants;
import storage.SiteConfig;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CrawlingMain {
    public static void main(String[] args) throws Exception {

        int maxFetch = 25;
        int threads = Runtime.getRuntime().availableProcessors(); // or choose manually

        // Load configs + robots rules
        List<SiteConfig> listOfConfigs = SiteConfig.generateConfigsWithRobots(null);
        if (listOfConfigs.isEmpty()) {
            info("No configs found. Exiting.");
            return;
        }

        info("Loaded " + listOfConfigs.size() + " site configs.");
        info("Starting thread pool with " + threads + " threads.");

        // Thread pool for concurrent crawling
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (SiteConfig cfg : listOfConfigs) {
            pool.submit(() -> {
                try {
                    info("Launching crawler for: " + cfg.baseUrl());
                    Crawler crawler = new Crawler(cfg, maxFetch, Constants.FileFormat.JSONL, true);
                    crawler.crawl();
                } catch (Exception e) {
                    info("Error running crawler for: " + cfg.baseUrl());
                    e.printStackTrace();
                }
            });
        }

        // Shutdown gracefully
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);

        info("All crawling jobs completed.");
    }

    public static void info(String msg) {
        System.out.println("[CRAWLING MAIN]" + msg);
    }
}
