package main.newsmap.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SavedNewsTest {
    @TempDir Path directory;
    private EventDTO event(String time) throws Exception {
        return new ObjectMapper().readValue("""
            {"id":"event-1","title":"A preserved story","status":"ARCHIVED","articles":[
              {"articleId":"article-1","title":"Report","url":"https://example.com/report","publishTime":"%s"}
            ]}
            """.formatted(time), EventDTO.class);
    }

    @Test void savedSnapshotsSurviveRestartAndAreIsolatedByUser() throws Exception {
        Path database = directory.resolve("library.db");
        var store = new SavedEventStore(database);
        var oldEvent = event("2020-01-01T00:00:00Z");
        store.save("alice", oldEvent);
        String savedAt = store.list("alice").getFirst().savedAt();
        store.save("alice", oldEvent);
        assertEquals(1, store.list("alice").size());
        assertEquals(savedAt, store.list("alice").getFirst().savedAt());
        assertTrue(store.list("bob").isEmpty());
        assertFalse(store.archive("bob", "event-1", true));
        assertTrue(store.archive("alice", "event-1", true));
        var reopened = new SavedEventStore(database);
        assertTrue(reopened.list("alice").getFirst().archived());
        assertEquals(oldEvent, reopened.list("alice").getFirst().event());
        reopened.remove("bob", "event-1");
        assertEquals(1, reopened.list("alice").size());
        reopened.archive("alice", "event-1", false);
        assertFalse(reopened.list("alice").getFirst().archived());
        reopened.remove("alice", "event-1");
        assertTrue(reopened.list("alice").isEmpty());
    }

    @Test void libraryRoutesRequireTheProfileOwner() {
        var controller = new SavedEventController(new SavedEventStore(directory.resolve("ownership.db")));
        assertThrows(ResponseStatusException.class, () -> controller.list("alice", null));
        assertThrows(ResponseStatusException.class, () -> controller.list("alice", () -> "bob"));
        assertThrows(ResponseStatusException.class, () -> controller.save("alice", "event-1", () -> "bob"));
        assertThrows(ResponseStatusException.class, () -> controller.archive("alice", "event-1", new SavedEventController.ArchiveRequest(true), () -> "bob"));
        assertThrows(ResponseStatusException.class, () -> controller.remove("alice", "event-1", () -> "bob"));
        assertTrue(controller.list("alice", () -> "alice").isEmpty());
    }

    @Test void liveWindowUsesActualTimeAndDoesNotExpireStoredCopies() throws Exception {
        Instant now = Instant.parse("2026-11-05T12:00:00Z");
        assertTrue(EventWindow.isRecent(event("2026-11-03T12:00:00Z"), now));
        assertFalse(EventWindow.isRecent(event("2026-11-03T11:59:59Z"), now));
        assertFalse(EventWindow.isRecent(event("2026-11-05T12:00:01Z"), now));
        assertFalse(EventWindow.isRecent(event("invalid"), now));
        var store = new SavedEventStore(directory.resolve("old.db"));
        store.save("alice", event("2020-01-01T00:00:00Z"));
        assertEquals(1, store.list("alice").size());
    }
}
