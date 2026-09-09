package main.newsmap.web;

import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import main.newsmap.web.dto.PersonalizationDTO.FollowDTO;

import java.util.Locale;

final class FollowMatcher {
    private FollowMatcher() {}

    static boolean matches(EventDTO event, FollowDTO follow) {
        String value = follow.value().toLowerCase(Locale.ROOT);
        return switch (follow.type()) {
            case "EVENT" -> event.id().equalsIgnoreCase(follow.value());
            case "CATEGORY" -> event.category() != null && event.category().equalsIgnoreCase(follow.value());
            case "LOCATION" -> event.location() != null && event.location().toLowerCase(Locale.ROOT).contains(value);
            case "ENTITY" -> event.entities() != null && event.entities().stream()
                    .anyMatch(entity -> entity.equalsIgnoreCase(follow.value()));
            case "SOURCE" -> event.sources() != null && event.sources().stream()
                    .anyMatch(source -> source.equalsIgnoreCase(follow.value()));
            default -> false;
        };
    }

    static String reason(FollowDTO follow) {
        String label = follow.type().charAt(0) + follow.type().substring(1).toLowerCase(Locale.ROOT);
        return "Followed " + label + ": " + follow.value();
    }
}
