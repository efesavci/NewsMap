package crawler;

import global.Constants;
import storage.SiteConfig;

import java.util.List;

public class MainCrawling {
    public static void main(String[] args) throws Exception {

        int maxFetch = 5;
        List<SiteConfig> listOfConfigs = SiteConfig.generateConfigsWithRobots(null);
        for (SiteConfig siteConfig : listOfConfigs) {
            Crawler crawler = new Crawler(siteConfig, maxFetch, Constants.FileFormat.JSONL);
            crawler.crawl();
        }


    }
}
