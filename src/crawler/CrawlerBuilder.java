package crawler;

import storage.SiteConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static crawler.CrawlerTester.info;
import static crawler.CrawlerUtils.*;
import static global.Constants.*;

public class CrawlerBuilder {

    private int maxArticleCountForEach;

    private int numThreads;

    private List<SiteConfig> configList;

    private ExecutorService threadPool;

    private boolean runConcurrently;

    private List<Crawler> crawlerList = new ArrayList<>();

    public CrawlerBuilder(int maxArticleCountForEach, boolean runConcurrently, String configFile,FileFormat format){

        List<SiteConfig> listOfSiteConfigs = SiteConfig.generateConfigsWithRobots(configFile);
        if (listOfSiteConfigs.isEmpty()) {
            throw new IllegalArgumentException("[CRAWLER BUILDER]No configs found.");
        }
        this.configList = listOfSiteConfigs;

        if(runConcurrently) {
            numThreads = Runtime.getRuntime().availableProcessors();
            threadPool = Executors.newFixedThreadPool(numThreads);
        }
        else {
            numThreads = 1;
            threadPool = null;
        }
        this.maxArticleCountForEach = maxArticleCountForEach;

        for(SiteConfig cfg : configList) {
            builder_print("Setting Up Crawler for " + cfg.baseUrl());
            try {
                Crawler crawler = new Crawler(cfg, this.maxArticleCountForEach, format, this.runConcurrently);
                crawlerList.add(crawler);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create crawler for: " + cfg.baseUrl(), e);
            }
        }
    }

    public void startCrawl(){
        if(this.runConcurrently) {
            concurrentCrawl();
            return;
        }
        sequentialCrawl();
    }

    private void sequentialCrawl() {
        for(Crawler crawler : crawlerList) {
            builder_print("Launching Crawler for: " + crawler.getSiteConfig().baseUrl());
            crawler.crawl();
        }
    }

    private void concurrentCrawl() {
        try {
            for (Crawler crawler : crawlerList) {
                threadPool.submit(() -> {
                    builder_print("Launching Crawler for: " + crawler.getSiteConfig().baseUrl()
                            + "(Thread %d)".formatted(Thread.currentThread().threadId()));
                    crawler.crawl();
                });
            }
            threadPool.shutdown();
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new IllegalStateException("Concurrent crawling failed", e);
        }
    }


    public CrawlerBuilder(int maxArticleCountForEach, boolean runConcurrently) {
        this(maxArticleCountForEach, runConcurrently, null,FileFormat.JSONL);
    }

    public CrawlerBuilder(int maxArticleCountForEach) {
        this(maxArticleCountForEach, true);
    }

    public static void builder_print(String msg) {
        System.out.println("[CRAWLER BUILDER]" + msg);
    }

    public int getNumThreads() {
        return numThreads;
    }

    public int getMaxArticleCountForEach() {
        return maxArticleCountForEach;
    }

    public boolean isRunConcurrently() {
        return runConcurrently;
    }
}
