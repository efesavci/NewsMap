package analysis.enrichment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistent cache and conservative lifetime spend ledger, shared across restarts. */
public final class EnrichmentStore {
    private final String url;
    public EnrichmentStore(Path path) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        url = "jdbc:sqlite:" + path.toAbsolutePath();
        try (var db = open()) {
            db.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS enrichment_cache (cache_key TEXT PRIMARY KEY, result_json TEXT NOT NULL, created_at TEXT NOT NULL)");
            db.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS enrichment_requests (id TEXT PRIMARY KEY, cache_key TEXT NOT NULL, charged_usd REAL NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL)");
        }
    }
    private Connection open() throws Exception {
        var db = DriverManager.getConnection(url);
        db.createStatement().execute("PRAGMA busy_timeout=5000");
        return db;
    }
    public Optional<String> cached(String key) throws Exception {
        try (var db = open(); var query = db.prepareStatement("SELECT result_json FROM enrichment_cache WHERE cache_key=?")) {
            query.setString(1,key);
            try (var rows = query.executeQuery()) { return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty(); }
        }
    }
    public double spent() throws Exception {
        try (var db = open(); var rows = db.createStatement().executeQuery("SELECT COALESCE(SUM(charged_usd),0) FROM enrichment_requests")) { rows.next(); return rows.getDouble(1); }
    }
    public String reserve(String key, double maximumCost, double budget) throws Exception {
        if (!Double.isFinite(maximumCost) || maximumCost <= 0 || !Double.isFinite(budget) || budget <= 0)
            throw new IllegalArgumentException("Invalid enrichment budget");
        try (var db = open()) {
            db.createStatement().execute("BEGIN IMMEDIATE");
            try {
                double used;
                try (var rows = db.createStatement().executeQuery("SELECT COALESCE(SUM(charged_usd),0) FROM enrichment_requests")) { rows.next(); used = rows.getDouble(1); }
                if (used + maximumCost > budget) throw new IllegalStateException("NewsMap enrichment budget reached");
                String id = UUID.randomUUID().toString();
                try (var insert = db.prepareStatement("INSERT INTO enrichment_requests VALUES (?,?,?,'RESERVED',?)")) {
                    insert.setString(1,id); insert.setString(2,key); insert.setDouble(3,maximumCost); insert.setString(4,Instant.now().toString()); insert.executeUpdate();
                }
                db.createStatement().execute("COMMIT");
                return id;
            } catch (Exception e) { db.createStatement().execute("ROLLBACK"); throw e; }
        }
    }
    public void finish(String id, String key, String validatedJson, Double actualCost) throws Exception {
        try (var db = open()) {
            db.setAutoCommit(false);
            try {
                if (validatedJson != null) try (var insert = db.prepareStatement("INSERT OR REPLACE INTO enrichment_cache VALUES (?,?,?)")) {
                    insert.setString(1,key); insert.setString(2,validatedJson); insert.setString(3,Instant.now().toString()); insert.executeUpdate();
                }
                // A timeout or missing usage keeps the full reservation charged.
                String sql = actualCost != null && Double.isFinite(actualCost) && actualCost >= 0
                        ? "UPDATE enrichment_requests SET status=?,charged_usd=? WHERE id=?"
                        : "UPDATE enrichment_requests SET status=? WHERE id=?";
                try (var update = db.prepareStatement(sql)) {
                    update.setString(1,validatedJson == null ? "FAILED" : "COMPLETE");
                    if (sql.contains("charged_usd=?")) { update.setDouble(2,actualCost); update.setString(3,id); }
                    else update.setString(2,id);
                    update.executeUpdate();
                }
                db.commit();
            } catch (Exception e) { db.rollback(); throw e; }
        }
    }
}
