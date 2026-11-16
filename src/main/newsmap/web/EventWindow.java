package main.newsmap.web;

import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import java.time.Duration;
import java.time.Instant;

final class EventWindow {
    private EventWindow() {}
    static boolean isRecent(EventDTO event, Instant now) {
        if (event.articles() == null) return false;
        Instant cutoff = now.minus(Duration.ofHours(48));
        return event.articles().stream().anyMatch(article -> {
            try {
                Instant published = Instant.parse(article.publishTime());
                return !published.isBefore(cutoff) && !published.isAfter(now);
            } catch (RuntimeException ignored) { return false; }
        });
    }
}
