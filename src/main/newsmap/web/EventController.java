package main.newsmap.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import main.newsmap.web.dto.EventFeedDTO;
import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    private static final Path EVENTS_FILE = Path.of("embeddings-service/outputs/events.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping
    public EventFeedDTO getEvents(
            @RequestParam(name = "status", defaultValue = "ACTIVE") String status,
            @RequestParam(name = "minArticles", defaultValue = "1") int minArticles
    ) {
        EventFeedDTO feed = readFeed();
        List<EventDTO> filtered = feed.events().stream()
                .filter(event -> status.isBlank() || event.status().equalsIgnoreCase(status))
                .filter(event -> event.articleCount() >= Math.max(1, minArticles))
                .toList();
        return new EventFeedDTO(feed.generatedAt(), feed.locationGeneratedAt(), feed.engineVersion(), filtered);
    }

    @GetMapping("/{eventId}")
    public EventDTO getEvent(@PathVariable String eventId) {
        return readFeed().events().stream()
                .filter(event -> event.id().equals(eventId))
                .findFirst()
                .orElseThrow(EventNotFoundException::new);
    }

    static EventFeedDTO readFeed() {
        if (!Files.exists(EVENTS_FILE)) {
            return new EventFeedDTO(null, null, "persistent-events-v2", List.of());
        }
        try {
            return MAPPER.readValue(EVENTS_FILE.toFile(), EventFeedDTO.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read persistent event projection", e);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class EventNotFoundException extends RuntimeException {}
}
