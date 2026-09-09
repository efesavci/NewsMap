package main.newsmap.web;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class EventQualityStore {
    public record FeedbackRequest(String eventId, String articleId, String verdict) {}
    public record FeedbackDTO(
            String feedbackId, String userId, String eventId, String articleId,
            String verdict, String createdAt
    ) {}
    public record QualitySummaryDTO(
            long reviewedArticles, long confirmedMatches, long rejectedMatches,
            double agreementRate
    ) {}

    private static final Path DATABASE = Path.of("data/event-quality.db");
    private static final Set<String> VERDICTS = Set.of("SAME_EVENT", "WRONG_EVENT");

    public EventQualityStore() {
        try {
            Files.createDirectories(DATABASE.getParent());
            try (Connection connection = open()) {
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS event_feedback (
                            feedback_id TEXT PRIMARY KEY,
                            user_id TEXT NOT NULL,
                            event_id TEXT NOT NULL,
                            article_id TEXT NOT NULL,
                            verdict TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            UNIQUE (user_id, article_id)
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_event_feedback_event
                        ON event_feedback (event_id, verdict)
                        """);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize event quality storage", e);
        }
    }

    public synchronized FeedbackDTO record(String userId, FeedbackRequest request) {
        if (request == null || blank(request.eventId()) || blank(request.articleId())) {
            throw new IllegalArgumentException("Event and article are required");
        }
        String verdict = request.verdict() == null ? "" : request.verdict().trim().toUpperCase(Locale.ROOT);
        if (!VERDICTS.contains(verdict)) throw new IllegalArgumentException("Unknown quality verdict");
        String id = "fbk_" + UUID.randomUUID().toString().replace("-", "");
        String now = Instant.now().toString();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO event_feedback (
                         feedback_id, user_id, event_id, article_id, verdict, created_at
                     ) VALUES (?, ?, ?, ?, ?, ?)
                     ON CONFLICT(user_id, article_id) DO UPDATE SET
                         event_id = excluded.event_id,
                         verdict = excluded.verdict,
                         created_at = excluded.created_at
                     """)) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.setString(3, request.eventId());
            statement.setString(4, request.articleId());
            statement.setString(5, verdict);
            statement.setString(6, now);
            statement.executeUpdate();
            return new FeedbackDTO(id, userId, request.eventId(), request.articleId(), verdict, now);
        } catch (Exception e) {
            throw new IllegalStateException("Could not save event feedback", e);
        }
    }

    public synchronized QualitySummaryDTO summary() {
        try (Connection connection = open();
             ResultSet result = connection.createStatement().executeQuery("""
                     SELECT COUNT(*) total,
                         SUM(CASE WHEN verdict = 'SAME_EVENT' THEN 1 ELSE 0 END) confirmed,
                         SUM(CASE WHEN verdict = 'WRONG_EVENT' THEN 1 ELSE 0 END) rejected
                     FROM event_feedback
                     """)) {
            result.next();
            long total = result.getLong("total");
            long confirmed = result.getLong("confirmed");
            long rejected = result.getLong("rejected");
            return new QualitySummaryDTO(total, confirmed, rejected, total == 0 ? 0 : confirmed / (double) total);
        } catch (Exception e) {
            throw new IllegalStateException("Could not summarize event feedback", e);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + DATABASE.toAbsolutePath());
    }
}
