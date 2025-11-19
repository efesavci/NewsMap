package crawler;

import global.Constants;
import storage.SiteConfig;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CrawlerTester {
    public static void main(String[] args) throws Exception {

        CrawlerBuilder builder = new CrawlerBuilder(200,true);
        builder.startCrawl();
    }

    public static void info(String msg) {
        System.out.println("[CRAWLING MAIN]" + msg);
    }
}
