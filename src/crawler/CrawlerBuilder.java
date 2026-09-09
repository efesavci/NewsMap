package crawler;

import storage.SiteConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static crawler.CrawlerUtils.*;
import static crawler.CrawlerTester.info;
import static global.Constants.*;

public class CrawlerBuilder {
    public record CrawlResult(List<Crawler.CrawlStats> sources, long ledgerTotal) {
        public int fetched() { return sources.stream().mapToInt(Crawler.CrawlStats::fetched).sum(); }
        public int candidates() { return sources.stream().mapToInt(Crawler.CrawlStats::candidates).sum(); }
        public int skippedKnown() { return sources.stream().mapToInt(Crawler.CrawlStats::skippedKnown).sum(); }
        public int empty() { return sources.stream().mapToInt(Crawler.CrawlStats::empty).sum(); }
        public int failed() { return sources.stream().mapToInt(Crawler.CrawlStats::failed).sum(); }
    }


    private final int maxArticleCountForEach;
    private final boolean runConcurrently;
    private final List<SiteConfig> configList;
    private final List<Crawler> crawlerList = new ArrayList<>();
    private final CrawlLedger ledger;

    public CrawlerBuilder(int maxArticleCountForEach, boolean runConcurrently, String configFile, FileFormat format) {

        List<SiteConfig> listOfSiteConfigs = SiteConfig.generateConfigsWithRobots(configFile);
        if (listOfSiteConfigs.isEmpty()) {
            throw new IllegalArgumentException("[CRAWLER BUILDER] No configs found.");
        }

        this.configList = listOfSiteConfigs;
        this.runConcurrently = runConcurrently;
        this.maxArticleCountForEach = maxArticleCountForEach;
        this.ledger = new CrawlLedger(Path.of("data/crawl-ledger.db"));
        Instant crawlRunTimestamp = Instant.now();

        for (SiteConfig cfg : configList) {
            builder_print("Setting Up Crawler for " + cfg.baseUrl());
            try {
                Crawler crawler = new Crawler(
                        cfg, this.maxArticleCountForEach, format, this.runConcurrently,
                        crawlRunTimestamp, ledger
                );
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

    public CrawlResult startCrawl() {
        if (this.runConcurrently) {
            return concurrentCrawl();
        } else {
            return sequentialCrawl();
        }
    }

    private CrawlResult sequentialCrawl() {
        List<Crawler.CrawlStats> results = new ArrayList<>();
        for (Crawler crawler : crawlerList) {
            builder_print("Launching Crawler for: " + crawler.getSiteConfig().baseUrl());
            results.add(crawler.crawl());
        }
        return new CrawlResult(List.copyOf(results), ledger.size());
    }

    private CrawlResult concurrentCrawl() {
        int numCrawlers = crawlerList.size();

        builder_print("Starting concurrent crawling with " + numCrawlers + " threads.");

        ExecutorService pool = Executors.newFixedThreadPool(numCrawlers, r -> {
            Thread t = new Thread(r);
            t.setName("CrawlerThread-" + t.getId());
            return t;
        });

        List<Callable<Crawler.CrawlStats>> tasks = new ArrayList<>();

        for (Crawler crawler : crawlerList) {
            tasks.add(() -> {
                String name = crawler.getSiteConfig().baseUrl();
                builder_print("Starting: " + name + " on " + Thread.currentThread().getName());
                try {
                    Crawler.CrawlStats stats = crawler.crawl();
                    builder_print("Finished: " + name);
                    return stats;
                } catch (Exception e) {
                    builder_print("FAILED: " + name + " — " + e.getMessage());
                    throw e;
                }
            });
        }

        List<Crawler.CrawlStats> results = new ArrayList<>();
        try {
            // invokeAll returns when ALL tasks finish or timeout hits
            List<Future<Crawler.CrawlStats>> futures = pool.invokeAll(tasks, 5, TimeUnit.MINUTES);


            for (Future<Crawler.CrawlStats> f : futures) {
                try {
                    results.add(f.get());
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
        CrawlResult result = new CrawlResult(List.copyOf(results), ledger.size());
        builder_print(">>> New articles in this run: " + result.fetched() +
                " (known skipped: " + result.skippedKnown() + ", ledger total: " + result.ledgerTotal() + ")");
        return result;
    }

    public static void builder_print(String msg) {
        System.out.println("[CRAWLER BUILDER] " + msg);
    }

}
