package crawler;

import org.apache.commons.codec.digest.DigestUtils;
import storage.Article;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Durable URL ledger used to stop scheduled crawl runs from downloading and
 * embedding the same article repeatedly.
 */
public final class CrawlLedger {
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "ref_src"
    );

    private final Path database;
    private final Set<String> claimedThisRun = new HashSet<>();

    public CrawlLedger(Path database) {
        this.database = database;
        try {
            Path parent = database.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Connection connection = open()) {
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS fetched_articles (
                            canonical_url TEXT PRIMARY KEY,
                            article_id TEXT NOT NULL,
                            source TEXT NOT NULL,
                            published_at TEXT,
                            content_hash TEXT NOT NULL,
                            first_fetched_at TEXT NOT NULL,
                            last_fetched_at TEXT NOT NULL
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_fetched_articles_source_time
                        ON fetched_articles (source, published_at)
                        """);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize crawl ledger at " + database, e);
        }
    }

    /**
     * Atomically claims a URL for this crawl run. A failed fetch can release
     * the claim so another discovery during the same run may retry it.
     */
    public synchronized boolean claimIfNew(String rawUrl) {
        String canonicalUrl = canonicalize(rawUrl);
        if (claimedThisRun.contains(canonicalUrl) || contains(canonicalUrl)) return false;
        claimedThisRun.add(canonicalUrl);
        return true;
    }

    public synchronized void releaseClaim(String rawUrl) {
        claimedThisRun.remove(canonicalize(rawUrl));
    }

    public synchronized void record(Article article) {
        String canonicalUrl = canonicalize(article.url());
        String contentHash = DigestUtils.sha256Hex(
                (article.title() == null ? "" : article.title()) + "\n" +
                (article.body() == null ? "" : article.body())
        );
        String now = Instant.now().toString();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO fetched_articles (
                         canonical_url, article_id, source, published_at, content_hash,
                         first_fetched_at, last_fetched_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(canonical_url) DO UPDATE SET
                         article_id = excluded.article_id,
                         source = excluded.source,
                         published_at = excluded.published_at,
                         content_hash = excluded.content_hash,
                         last_fetched_at = excluded.last_fetched_at
                     """)) {
            statement.setString(1, canonicalUrl);
            statement.setString(2, article.id());
            statement.setString(3, article.source());
            statement.setString(4, article.publishTime());
            statement.setString(5, contentHash);
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not record fetched article", e);
        }
    }

    public synchronized long size() {
        try (Connection connection = open();
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM fetched_articles")) {
            return result.next() ? result.getLong(1) : 0;
        } catch (Exception e) {
            throw new IllegalStateException("Could not count crawl ledger entries", e);
        }
    }

    private boolean contains(String canonicalUrl) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM fetched_articles WHERE canonical_url = ?")) {
            statement.setString(1, canonicalUrl);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not query crawl ledger", e);
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    public static String canonicalize(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim()).normalize();
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) port = -1;
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) path = "/";
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            String query = canonicalQuery(uri.getRawQuery());
            return new URI(scheme, uri.getRawUserInfo(), host, port, path, query, null).toASCIIString();
        } catch (Exception ignored) {
            int fragment = rawUrl.indexOf('#');
            return (fragment >= 0 ? rawUrl.substring(0, fragment) : rawUrl).trim();
        }
    }

    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        List<String> parts = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part.isBlank()) continue;
            String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (key.startsWith("utm_") || TRACKING_PARAMETERS.contains(key)) continue;
            parts.add(part);
        }
        parts.sort(Comparator.naturalOrder());
        return parts.isEmpty() ? null : String.join("&", parts);
    }
}
