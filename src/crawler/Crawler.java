package crawler;

import global.Constants;
import org.jetbrains.annotations.Nullable;
import storage.Article;
import storage.SiteConfig;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import static global.Constants.*;
import java.net.URI;

public class Crawler {
    /** One global timestamp for this crawl batch (same across all sites). */
    public static final Instant CRAWL_RUN_TIMESTAMP = Instant.now();

    private final SiteConfig config;
    private final Set<String> visited = new HashSet<>();

    private int maxArticlesToFetch;
    private int currentArticlesFetched = 0;

    private final Constants.FileFormat outputFormat;
    private final boolean outputAsBatch;

    @Nullable
    private final File batchFile;

    @Nullable
    private final Writer batchFileWriter;

    public Crawler(SiteConfig config, int maxArticlesToFetch, FileFormat outputFormat) throws IOException{
        System.out.println(CRAWLER_PRINT_PREFIX + "Initializing Crawler for " + config.baseUrl());
        this.config = config;
        this.maxArticlesToFetch = maxArticlesToFetch;
        this.outputFormat = outputFormat;
        this.outputAsBatch = switch (outputFormat) {
            case JSONL, PARQUET -> true;
            default -> false;
        };
        if (outputAsBatch) {
            String extension = FileFormat.getExtensionFromFormat(outputFormat);
            this.batchFile = new File("data/articles/articles_" +
                    batchFileTimeStampFormatter.format(CRAWL_RUN_TIMESTAMP) + extension);

            if (!batchFile.exists()) {
                batchFile.getParentFile().mkdirs();
                batchFile.createNewFile();
            }

            this.batchFileWriter = new BufferedWriter(new FileWriter(batchFile, true));
        } else {
            this.batchFile = null;
            this.batchFileWriter = null;
        }
    }

    public void crawl() throws Exception {
        System.out.println(CRAWLER_PRINT_PREFIX + "Starting crawl for: " + config.baseUrl());
        try {
            crawl(config.baseUrl(), 0);
        } catch (Exception e) {
            System.err.println(CRAWLER_PRINT_PREFIX + "Fatal error during crawl: " + e.getMessage());
            e.printStackTrace();
        } finally {
            close();
        }
    }

    /** Main recursive crawler entry point */
    public void crawl(String url, int depth) throws Exception {
        if (depth > config.maxDepth()) {
            System.out.println(CRAWLER_PRINT_PREFIX + "Max depth reached @ " + url);
            return;
        }
        if (isMaxArticlesReached()) return;
        if (visited.contains(url)) return;
        if (!isAllowed(url)) {
            System.out.println(CRAWLER_PRINT_PREFIX + "Skipping disallowed URL: " + url);
            return;
        }

        visited.add(url);

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .timeout(10_000)
                    .userAgent(USER_AGENT)
                    .get();
        } catch (IOException e) {
            System.err.println(CRAWLER_PRINT_PREFIX + "Failed to fetch page: " + url + " (" + e.getMessage() + ")");
            return;
        }

        extractArticles(doc);
        if (isMaxArticlesReached()) return;

        followTopics(doc, depth);
    }

    //===========================================
    // Extract Article Pages
    //===========================================
    private void extractArticles(Document doc) {
        for (String sel : config.articleSelectors()) {
            for (Element link : doc.select(sel)) {

                String articleUrl = link.absUrl("href");
                if (articleUrl.isEmpty() || visited.contains(articleUrl)) continue;
                if (!isAllowed(articleUrl)) continue;

                visited.add(articleUrl);
                try {
                    Document articleDoc = Jsoup.connect(articleUrl)
                            .timeout(10_000)
                            .userAgent(USER_AGENT)
                            .get();

                    Article article = parse(articleDoc, articleUrl);

                    switch (outputFormat) {
                        case JSON -> article.saveAsSingleJSON();
                        case JSONL -> article.appendToJsonBatch(batchFileWriter);
                        // case PARQUET -> article.appendToParquetBatch();
                    }

                    System.out.println(CRAWLER_PRINT_PREFIX + "Saved article: " + articleUrl);
                    currentArticlesFetched++;

                } catch (IOException e) {
                    System.err.println(CRAWLER_PRINT_PREFIX + "I/O error @ " + articleUrl + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println(CRAWLER_PRINT_PREFIX + "Unexpected error parsing article: " + articleUrl);
                    e.printStackTrace();
                }

                if (isMaxArticlesReached()) return;
            }
        }
    }


    //===========================================
    // Follow Topic / Category Links
    //===========================================
    private void followTopics(Document doc, int depth) {
        for (String sel : config.topicSelectors()) {
            for (Element link : doc.select(sel)) {

                String topicUrl = link.absUrl("href");
                if (topicUrl.isEmpty() || visited.contains(topicUrl)) continue;

                try {
                    crawl(topicUrl, depth + 1);
                } catch (Exception e) {
                    System.err.println(CRAWLER_PRINT_PREFIX + "Error crawling topic: " + topicUrl);
                    e.printStackTrace();
                }

                if (isMaxArticlesReached()) return;
            }
        }
    }

    //===========================================
    // Helpers
    //===========================================
    private boolean isMaxArticlesReached() {
        if (currentArticlesFetched >= maxArticlesToFetch) {
            System.out.printf("%sMax articles reached: %d%n", CRAWLER_PRINT_PREFIX, currentArticlesFetched);
            return true;
        }
        return false;
    }
    /**
     * Local check for robots.txt permissions using the rules inside SiteConfig.
     * Returns true if URL is allowed for this crawler's user agent.
     */
    private boolean isAllowed(String url) {
        try {
            if (config.rules() == null) {
                System.err.println(CRAWLER_PRINT_PREFIX+"No robots rules loaded for: " + config.baseUrl());
                return false;
            }
            return config.rules().isAllowed(url);
        } catch (Exception e) {
            System.err.println(CRAWLER_PRINT_PREFIX+"Robots check failed for " + url + " (" + e.getMessage() + ")");
            return false; // safest default
        }
    }

    public void close() {
        if (batchFileWriter != null) {
            try {
                batchFileWriter.close();
            } catch (IOException ignored) {}
        }
    }


    /** Parses an article Document into an Article record */
    private Article parse(Document doc, String url) throws MalformedURLException {
        String title = doc.select(config.articleTitle()).text();
        String timeRaw = doc.select(config.articleTime()).attr("datetime");
        String body = doc.select(config.articleBody()).text();


        String id = DigestUtils.sha256Hex(url);
        String host = URI.create(config.baseUrl()).toURL().getHost();
        if(host == null) host = "unknown";

        Instant publishedAt;
        try {
            publishedAt = Instant.parse(timeRaw);
        } catch (Exception e) {
            publishedAt = Instant.now();
        }
        // current UTC timestamp
        return new Article(
                id,
                url,
                title,
                body,
                host,
                timeStampFormatter.format(publishedAt),
                timeStampFormatter.format(Instant.now())
        );
    }


}
