package main.newsmap.web;

import crawler.Crawler.CrawlStats;
import crawler.CrawlerBuilder.CrawlResult;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PipelineRunStore {
    public record EventSnapshot(int events, int articles) {}
    public record SourceRunDTO(
            String source, int candidates, int newArticles, int knownSkipped,
            int emptyArticles, int failedArticles, long durationMs
    ) {}
    public record PipelineRunDTO(
            String runId, String trigger, String status, String startedAt, String completedAt,
            long durationMs, int candidates, int newArticles, int knownSkipped,
            int emptyArticles, int failedArticles, long ledgerTotal,
            int eventsBefore, int eventsAfter, int eventArticlesBefore, int eventArticlesAfter,
            String error, List<SourceRunDTO> sources
    ) {}

    private static final Path DATABASE = Path.of("data/pipeline-runs.db");

    public PipelineRunStore() {
        try {
            Files.createDirectories(DATABASE.getParent());
            try (Connection connection = open()) {
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS pipeline_runs (
                            run_id TEXT PRIMARY KEY,
                            trigger_type TEXT NOT NULL,
                            status TEXT NOT NULL,
                            started_at TEXT NOT NULL,
                            completed_at TEXT,
                            duration_ms INTEGER NOT NULL DEFAULT 0,
                            candidates INTEGER NOT NULL DEFAULT 0,
                            new_articles INTEGER NOT NULL DEFAULT 0,
                            known_skipped INTEGER NOT NULL DEFAULT 0,
                            empty_articles INTEGER NOT NULL DEFAULT 0,
                            failed_articles INTEGER NOT NULL DEFAULT 0,
                            ledger_total INTEGER NOT NULL DEFAULT 0,
                            events_before INTEGER NOT NULL DEFAULT 0,
                            events_after INTEGER NOT NULL DEFAULT 0,
                            event_articles_before INTEGER NOT NULL DEFAULT 0,
                            event_articles_after INTEGER NOT NULL DEFAULT 0,
                            error TEXT
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS pipeline_source_runs (
                            run_id TEXT NOT NULL REFERENCES pipeline_runs(run_id) ON DELETE CASCADE,
                            source TEXT NOT NULL,
                            candidates INTEGER NOT NULL,
                            new_articles INTEGER NOT NULL,
                            known_skipped INTEGER NOT NULL,
                            empty_articles INTEGER NOT NULL,
                            failed_articles INTEGER NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            PRIMARY KEY (run_id, source)
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_pipeline_runs_started
                        ON pipeline_runs (started_at DESC)
                        """);
                connection.createStatement().executeUpdate("""
                        UPDATE pipeline_runs SET status = 'INTERRUPTED', completed_at = started_at,
                            error = COALESCE(error, 'Application stopped before this run completed')
                        WHERE status = 'RUNNING'
                        """);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize pipeline run storage", e);
        }
    }

    public synchronized String start(String trigger, EventSnapshot before) {
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO pipeline_runs (
                         run_id, trigger_type, status, started_at,
                         events_before, event_articles_before
                     ) VALUES (?, ?, 'RUNNING', ?, ?, ?)
                     """)) {
            statement.setString(1, runId);
            statement.setString(2, trigger);
            statement.setString(3, Instant.now().toString());
            statement.setInt(4, before.events());
            statement.setInt(5, before.articles());
            statement.executeUpdate();
            return runId;
        } catch (Exception e) {
            throw new IllegalStateException("Could not start pipeline run record", e);
        }
    }

    public synchronized void finish(
            String runId,
            String status,
            CrawlResult crawl,
            EventSnapshot after,
            String error
    ) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE pipeline_runs SET
                        status = ?, completed_at = ?,
                        duration_ms = CAST((julianday(?) - julianday(started_at)) * 86400000 AS INTEGER),
                        candidates = ?, new_articles = ?, known_skipped = ?,
                        empty_articles = ?, failed_articles = ?, ledger_total = ?,
                        events_after = ?, event_articles_after = ?, error = ?
                    WHERE run_id = ?
                    """)) {
                String completedAt = Instant.now().toString();
                update.setString(1, status);
                update.setString(2, completedAt);
                update.setString(3, completedAt);
                update.setInt(4, crawl == null ? 0 : crawl.candidates());
                update.setInt(5, crawl == null ? 0 : crawl.fetched());
                update.setInt(6, crawl == null ? 0 : crawl.skippedKnown());
                update.setInt(7, crawl == null ? 0 : crawl.empty());
                update.setInt(8, crawl == null ? 0 : crawl.failed());
                update.setLong(9, crawl == null ? 0 : crawl.ledgerTotal());
                update.setInt(10, after.events());
                update.setInt(11, after.articles());
                update.setString(12, error);
                update.setString(13, runId);
                update.executeUpdate();
            }
            if (crawl != null) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO pipeline_source_runs (
                            run_id, source, candidates, new_articles, known_skipped,
                            empty_articles, failed_articles, duration_ms
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (CrawlStats source : crawl.sources()) {
                        insert.setString(1, runId);
                        insert.setString(2, source.source());
                        insert.setInt(3, source.candidates());
                        insert.setInt(4, source.fetched());
                        insert.setInt(5, source.skippedKnown());
                        insert.setInt(6, source.empty());
                        insert.setInt(7, source.failed());
                        insert.setLong(8, source.durationMs());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            connection.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Could not finish pipeline run record", e);
        }
    }

    public synchronized List<PipelineRunDTO> list(int rawLimit) {
        int limit = Math.max(1, Math.min(rawLimit, 100));
        List<PipelineRunDTO> runs = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM pipeline_runs ORDER BY started_at DESC LIMIT ?
                     """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) runs.add(fromResult(connection, result));
            }
            return runs;
        } catch (Exception e) {
            throw new IllegalStateException("Could not list pipeline runs", e);
        }
    }

    private PipelineRunDTO fromResult(Connection connection, ResultSet result) throws Exception {
        List<SourceRunDTO> sources = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM pipeline_source_runs WHERE run_id = ? ORDER BY source
                """)) {
            statement.setString(1, result.getString("run_id"));
            try (ResultSet source = statement.executeQuery()) {
                while (source.next()) {
                    sources.add(new SourceRunDTO(
                            source.getString("source"), source.getInt("candidates"),
                            source.getInt("new_articles"), source.getInt("known_skipped"),
                            source.getInt("empty_articles"), source.getInt("failed_articles"),
                            source.getLong("duration_ms")
                    ));
                }
            }
        }
        return new PipelineRunDTO(
                result.getString("run_id"), result.getString("trigger_type"), result.getString("status"),
                result.getString("started_at"), result.getString("completed_at"), result.getLong("duration_ms"),
                result.getInt("candidates"), result.getInt("new_articles"), result.getInt("known_skipped"),
                result.getInt("empty_articles"), result.getInt("failed_articles"), result.getLong("ledger_total"),
                result.getInt("events_before"), result.getInt("events_after"),
                result.getInt("event_articles_before"), result.getInt("event_articles_after"),
                result.getString("error"), sources
        );
    }

    private Connection open() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA foreign_keys = ON");
        }
        return connection;
    }
}
