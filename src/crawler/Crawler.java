package crawler;
import org.jetbrains.annotations.Nullable;
import storage.Article;
import storage.SiteConfig;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.MalformedURLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import static global.Constants.*;
import java.net.URI;
import static crawler.CrawlerUtils.*;

public class Crawler {
    public record CrawlStats(
            String source,
            int candidates,
            int fetched,
            int skippedKnown,
            int empty,
            int failed,
            long durationMs
    ) {}

    private final SiteConfig config;
    private final Set<String> visited = new HashSet<>();
    private final CrawlLedger ledger;

    private int maxArticlesToFetch;
    private int currentArticlesFetched = 0;
    private int articleCandidates = 0;
    private int skippedKnown = 0;
    private int emptyArticles = 0;
    private int failedArticles = 0;

    private final FileFormat outputFormat;
    private final boolean outputAsBatch;

    @Nullable
    private final File batchFile;

    @Nullable
    private final Writer batchFileWriter;

    public Crawler(
            SiteConfig config,
            int maxArticlesToFetch,
            FileFormat outputFormat,
            boolean isConcurrent,
            Instant crawlRunTimestamp,
            CrawlLedger ledger
    ) throws IOException{
        crawler_info("Initializing Crawler for " + config.baseUrl());
        this.config = config;
        this.maxArticlesToFetch = maxArticlesToFetch;
        this.outputFormat = outputFormat;
        this.ledger = ledger;
        this.outputAsBatch = switch (outputFormat) {
            case JSONL, PARQUET -> true;
            default -> false;
        };
        if (outputAsBatch) {
            String extension = FileFormat.getExtensionFromFormat(outputFormat);
            this.batchFile = createBatchFile(config, extension, crawlRunTimestamp, isConcurrent);
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

    public CrawlStats crawl() {
        long startedAt = System.currentTimeMillis();
        crawler_info("Starting crawl for: " + config.baseUrl());
        try {
            crawl(config.baseUrl(), 0);
        } catch (Exception e) {
            crawler_error("Fatal crawler_error during crawl: " + e.getMessage());
            e.printStackTrace();
        } finally {
            close();
        }
        return new CrawlStats(
                config.baseUrl(), articleCandidates, currentArticlesFetched, skippedKnown,
                emptyArticles, failedArticles, System.currentTimeMillis() - startedAt
        );
    }

    /** Main recursive crawler entry point */
    public void crawl(String url, int depth) throws Exception {
        if (depth > config.maxDepth()) {
            crawler_info("Max depth reached @ " + url);
            return;
        }
        if (isMaxArticlesReached()) return;
        if (visited.contains(url)) return;
        if (!isAllowed(url)) {
            crawler_warn("Skipping disallowed URL: " + url);
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
            crawler_error("Failed to fetch page: " + url + " (" + e.getMessage() + ")");
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

                String articleUrl = CrawlLedger.canonicalize(link.absUrl("href"));

                if (articleUrl.isEmpty() || visited.contains(articleUrl)) continue;
                if (!isAllowed(articleUrl)) continue;
                articleCandidates++;
                if (!ledger.claimIfNew(articleUrl)) {
                    visited.add(articleUrl);
                    skippedKnown++;
                    if (isMaxArticlesReached()) return;
                    continue;
                }

                visited.add(articleUrl);
                try {
                    Document articleDoc = Jsoup.connect(articleUrl)
                            .timeout(10_000)
                            .userAgent(USER_AGENT)
                            .get();

                    Article article = parse(articleDoc, articleUrl);

                    if (article.body() == null || article.body().isBlank()) {
                        ledger.releaseClaim(articleUrl);
                        emptyArticles++;
                        crawler_warn("Skipping article with an empty body: " + articleUrl);
                        continue;
                    }

                    switch (outputFormat) {
                        case JSON -> article.saveAsSingleJSON();
                        case JSONL -> article.appendToJsonBatch(batchFileWriter);
                        // case PARQUET -> article.appendToParquetBatch();
                    }

                    ledger.record(article);

                    crawler_info("Saved article: " + articleUrl);
                    currentArticlesFetched++;

                } catch (IOException e) {
                    ledger.releaseClaim(articleUrl);
                    failedArticles++;
                    crawler_error("I/O crawler_error @ " + articleUrl + ": " + e.getMessage());
                } catch (Exception e) {
                    ledger.releaseClaim(articleUrl);
                    failedArticles++;
                    crawler_error("Unexpected crawler_error parsing article: " + articleUrl);
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
                    crawler_error("Error crawling topic: " + topicUrl);
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
        if (articleCandidates >= maxArticlesToFetch) {
            crawler_info("Article candidate budget reached: " + articleCandidates +
                    " (new: " + currentArticlesFetched + ", known: " + skippedKnown + ")");
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
                crawler_error("No robots rules loaded for: " + config.baseUrl());
                return false;
            }
            return config.rules().isAllowed(url);
        } catch (Exception e) {
            crawler_error("Robots check failed for " + url + " (" + e.getMessage() + ")");
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

        // --- Timestamp extraction ---
        Element timeEl = doc.select(config.articleTime()).first();
        String time = extractTimeAttribute(timeEl);  // use helper below
        Instant publishedAt = parseSmartTimestamp(time);

        // --- Parse article body ---
        String body = doc.select(config.articleBody()).text();

        // --- ID + host extraction ---
        String id = DigestUtils.sha256Hex(url);
        String host = URI.create(config.baseUrl()).toURL().getHost();
        if (host == null) host = "unknown";

        // --- Timestamp parsing ---
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


    public SiteConfig getSiteConfig() {
        return config;
    }









}
