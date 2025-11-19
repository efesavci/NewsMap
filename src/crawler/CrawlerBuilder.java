package crawler;

import storage.SiteConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static crawler.CrawlerUtils.*;
import static crawler.CrawlerTester.info;
import static global.Constants.*;

public class CrawlerBuilder {

    private final int maxArticleCountForEach;
    private final boolean runConcurrently;
    private final List<SiteConfig> configList;
    private final List<Crawler> crawlerList = new ArrayList<>();

    public CrawlerBuilder(int maxArticleCountForEach, boolean runConcurrently, String configFile, FileFormat format) {

        List<SiteConfig> listOfSiteConfigs = SiteConfig.generateConfigsWithRobots(configFile);
        if (listOfSiteConfigs.isEmpty()) {
            throw new IllegalArgumentException("[CRAWLER BUILDER] No configs found.");
        }

        this.configList = listOfSiteConfigs;
        this.runConcurrently = runConcurrently;
        this.maxArticleCountForEach = maxArticleCountForEach;

        for (SiteConfig cfg : configList) {
            builder_print("Setting Up Crawler for " + cfg.baseUrl());
            try {
                Crawler crawler = new Crawler(cfg, this.maxArticleCountForEach, format, this.runConcurrently);
                crawlerList.add(crawler);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create crawler for: " + cfg.baseUrl(), e);
            }
        }
    }

    public CrawlerBuilder(int maxArticleCountForEach, boolean runConcurrently) {
        this(maxArticleCountForEach, runConcurrently, null, FileFormat.JSONL);
    }

    public CrawlerBuilder(int maxArticleCountForEach) {
        this(maxArticleCountForEach, true, null, FileFormat.JSONL);
    }

    public void startCrawl() {
        if (this.runConcurrently) {
            concurrentCrawl();
        } else {
            sequentialCrawl();
        }
    }

    private void sequentialCrawl() {
        for (Crawler crawler : crawlerList) {
            builder_print("Launching Crawler for: " + crawler.getSiteConfig().baseUrl());
            crawler.crawl();
        }
    }

    private void concurrentCrawl() {
        int numCrawlers = crawlerList.size();

        builder_print("Starting concurrent crawling with " + numCrawlers + " threads.");

        ExecutorService pool = Executors.newFixedThreadPool(numCrawlers, r -> {
            Thread t = new Thread(r);
            t.setName("CrawlerThread-" + t.getId());
            return t;
        });

        List<Callable<Void>> tasks = new ArrayList<>();

        for (Crawler crawler : crawlerList) {
            tasks.add(() -> {
                String name = crawler.getSiteConfig().baseUrl();
                builder_print("Starting: " + name + " on " + Thread.currentThread().getName());
                try {
                    crawler.crawl();
                    builder_print("Finished: " + name);
                } catch (Exception e) {
                    builder_print("FAILED: " + name + " — " + e.getMessage());
                    throw e;
                }
                return null;
            });
        }

        try {
            // invokeAll returns when ALL tasks finish or timeout hits
            List<Future<Void>> futures = pool.invokeAll(tasks, 5, TimeUnit.MINUTES);


            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException ee) {
                    throw new IllegalStateException("A crawler failed", ee.getCause());
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crawling interrupted", e);
        } finally {
            pool.shutdown();
        }

        builder_print(">>> All crawlers finished successfully.");
    }

    public static void builder_print(String msg) {
        System.out.println("[CRAWLER BUILDER] " + msg);
    }

}