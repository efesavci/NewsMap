package main.newsmap.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Saved snapshots have no expiry and remain independent of the live projection. */
@Component
public class SavedEventStore {
    public record SavedEvent(String eventId, String savedAt, boolean archived, EventDTO event) {}
    private final String databaseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public SavedEventStore() { this(Path.of("data/personalization.db")); }

    SavedEventStore(Path database) {
        databaseUrl = "jdbc:sqlite:" + database.toAbsolutePath();
        try {
            Files.createDirectories(database.toAbsolutePath().getParent());
            try (var connection = DriverManager.getConnection(databaseUrl)) {
                connection.createStatement().executeUpdate("""
                    CREATE TABLE IF NOT EXISTS saved_events (
                        user_id TEXT NOT NULL,
                        event_id TEXT NOT NULL,
                        saved_at TEXT NOT NULL,
                        archived INTEGER NOT NULL DEFAULT 0,
                        event_json TEXT NOT NULL,
                        PRIMARY KEY (user_id, event_id)
                    )
                    """);
            }
        } catch (Exception e) { throw new IllegalStateException("Could not initialize saved news", e); }
    }

    public synchronized List<SavedEvent> list(String userId) {
        try (var connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement("SELECT * FROM saved_events WHERE user_id = ? ORDER BY saved_at DESC, event_id")) {
            statement.setString(1, userId);
            List<SavedEvent> items = new ArrayList<>();
            try (var result = statement.executeQuery()) {
                while (result.next()) items.add(new SavedEvent(result.getString("event_id"), result.getString("saved_at"),
                        result.getInt("archived") == 1, mapper.readValue(result.getString("event_json"), EventDTO.class)));
            }
            return items;
        } catch (Exception e) { throw new IllegalStateException("Could not read saved news", e); }
    }

    public synchronized void save(String userId, EventDTO event) {
        try (var connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement("""
                 INSERT INTO saved_events (user_id, event_id, saved_at, event_json) VALUES (?, ?, ?, ?)
                 ON CONFLICT(user_id, event_id) DO NOTHING
                 """)) {
            statement.setString(1, userId);
            statement.setString(2, event.id());
            statement.setString(3, Instant.now().toString());
            statement.setString(4, mapper.writeValueAsString(event));
            statement.executeUpdate();
        } catch (Exception e) { throw new IllegalStateException("Could not save news", e); }
    }

    public synchronized boolean archive(String userId, String eventId, boolean archived) {
        try (var connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement("UPDATE saved_events SET archived = ? WHERE user_id = ? AND event_id = ?")) {
            statement.setBoolean(1, archived);
            statement.setString(2, userId);
            statement.setString(3, eventId);
            return statement.executeUpdate() > 0;
        } catch (Exception e) { throw new IllegalStateException("Could not archive news", e); }
    }

    public synchronized void remove(String userId, String eventId) {
        try (var connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement("DELETE FROM saved_events WHERE user_id = ? AND event_id = ?")) {
            statement.setString(1, userId);
            statement.setString(2, eventId);
            statement.executeUpdate();
        } catch (Exception e) { throw new IllegalStateException("Could not remove saved news", e); }
    }
}
