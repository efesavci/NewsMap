package main.newsmap.web;

import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import main.newsmap.web.dto.PersonalizationDTO.FollowDTO;
import main.newsmap.web.dto.PersonalizationDTO.RadarFeedDTO;
import main.newsmap.web.dto.PersonalizationDTO.RadarItemDTO;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/radar")
public class RadarController {
    private final PersonalizationStore store;

    public RadarController(PersonalizationStore store) {
        this.store = store;
    }

    @GetMapping
    public RadarFeedDTO getRadar(
            @RequestParam String userId,
            @RequestParam(defaultValue = "100") int limit,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        var profile = store.getProfile(userId);
        var feed = EventController.readFeed();
        Instant newest = feed.events().stream()
                .map(EventDTO::lastPublishedAt)
                .map(Instant::parse)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        List<RadarItemDTO> items = feed.events().stream()
                .filter(event -> "ACTIVE".equals(event.status()))
                .filter(event -> event.lat() != null && event.lon() != null)
                .filter(event -> !(event.lat() == 0 && event.lon() == 0))
                .map(event -> rank(event, profile.follows(), newest))
                .filter(item -> profile.follows().isEmpty() || item.reasons().stream()
                        .anyMatch(reason -> reason.startsWith("Followed ")))
                .sorted(Comparator.comparingDouble(RadarItemDTO::score).reversed()
                        .thenComparing(item -> item.event().lastPublishedAt(), Comparator.reverseOrder()))
                .limit(Math.max(1, Math.min(limit, 250)))
                .toList();
        return new RadarFeedDTO(userId, Instant.now().toString(), profile.follows(), items);
    }

    private RadarItemDTO rank(EventDTO event, List<FollowDTO> follows, Instant newest) {
        List<String> reasons = new ArrayList<>();
        double score = event.confidence() * 0.24;
        long ageHours = Math.max(0, Duration.between(Instant.parse(event.lastPublishedAt()), newest).toHours());
        score += Math.exp(-ageHours / 48.0) * 0.24;
        score += Math.min(0.14, Math.log1p(event.articleCount()) * 0.045 + event.sources().size() * 0.018);

        for (FollowDTO follow : follows) {
            if (FollowMatcher.matches(event, follow)) {
                score += followWeight(follow.type());
                reasons.add(FollowMatcher.reason(follow));
            }
        }
        if (reasons.isEmpty()) reasons.add(follows.isEmpty() ? "Recent high-confidence event" : "Radar discovery");
        return new RadarItemDTO(Math.round(Math.min(score, 1.0) * 1_000_000) / 1_000_000.0, reasons, event);
    }

    private double followWeight(String type) {
        return switch (type) {
            case "EVENT" -> 0.60;
            case "ENTITY" -> 0.42;
            case "LOCATION", "CATEGORY" -> 0.34;
            case "SOURCE" -> 0.24;
            default -> 0;
        };
    }
}
