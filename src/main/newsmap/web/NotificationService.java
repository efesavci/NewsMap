package main.newsmap.web;

import main.newsmap.web.dto.EventFeedDTO.EventDTO;
import main.newsmap.web.dto.EventFeedDTO.EventUpdateDTO;
import main.newsmap.web.dto.PersonalizationDTO.FollowDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationPreferencesDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationRefreshDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final PersonalizationStore store;

    public NotificationService(PersonalizationStore store) {
        this.store = store;
    }

    public NotificationRefreshDTO refreshUser(String userId) {
        var profile = store.getProfile(userId);
        if (profile.follows().isEmpty()) return new NotificationRefreshDTO(userId, 0);
        NotificationPreferencesDTO preferences = store.getNotificationPreferences(userId);

        int created = 0;
        for (EventDTO event : EventController.readFeed().events()) {
            if (event.updates() == null) continue;
            for (EventUpdateDTO update : event.updates()) {
                String priority = priorityFor(update);
                List<String> reasons = matchingReasons(event, update, profile.follows(), priority);
                if (reasons.isEmpty()) continue;
                var write = store.insertNotification(
                        userId, event.id(), update.updateId(), update.type(), priority, titleFor(update.type()),
                        event.title(), reasons, update.createdAt()
                );
                if (write.created()) created++;
                if (write.outboxEligible() && shouldQueueDelivery(preferences, priority)) {
                    store.enqueueDeliveryBundle(
                            userId, event.id(), write.notificationId(), priority, update.createdAt(),
                            deliveryAvailableAt(preferences, priority).toString()
                    );
                }
            }
        }
        return new NotificationRefreshDTO(userId, created);
    }

    private boolean shouldQueueDelivery(NotificationPreferencesDTO preferences, String priority) {
        if (!preferences.deliveryEnabled()) return false;
        return !"HIGH".equals(preferences.minimumPriority()) || "HIGH".equals(priority);
    }

    private Instant deliveryAvailableAt(NotificationPreferencesDTO preferences, String priority) {
        Instant bundledAt = Instant.now().plus(Duration.ofMinutes(5));
        if ("HIGH".equals(priority) || !preferences.quietHoursEnabled()) return bundledAt;

        ZoneId zone = ZoneId.of(preferences.timezone());
        ZonedDateTime local = bundledAt.atZone(zone);
        LocalTime start = LocalTime.parse(preferences.quietStart());
        LocalTime end = LocalTime.parse(preferences.quietEnd());
        if (!withinQuietHours(local.toLocalTime(), start, end)) return bundledAt;

        LocalDate endDate = local.toLocalDate();
        if (start.isAfter(end) && !local.toLocalTime().isBefore(start)) {
            endDate = endDate.plusDays(1);
        }
        ZonedDateTime quietEnd = ZonedDateTime.of(endDate, end, zone);
        if (!quietEnd.isAfter(local)) quietEnd = quietEnd.plusDays(1);
        return quietEnd.toInstant();
    }

    private boolean withinQuietHours(LocalTime value, LocalTime start, LocalTime end) {
        if (start.equals(end)) return true;
        if (start.isBefore(end)) return !value.isBefore(start) && value.isBefore(end);
        return !value.isBefore(start) || value.isBefore(end);
    }

    public void refreshAllUsers() {
        for (String userId : store.listUserIds()) {
            try {
                refreshUser(userId);
            } catch (Exception e) {
                System.err.printf("[NotificationService] Could not refresh %s: %s%n", userId, e.getMessage());
            }
        }
    }

    private List<String> matchingReasons(
            EventDTO event,
            EventUpdateDTO update,
            List<FollowDTO> follows,
            String priority
    ) {
        Instant updatedAt = Instant.parse(update.createdAt());
        List<String> reasons = new ArrayList<>();
        for (FollowDTO follow : follows) {
            if (updatedAt.isBefore(Instant.parse(follow.createdAt()))) continue;
            if (!acceptsPriority(follow, priority)) continue;
            if (!FollowMatcher.matches(event, follow)) continue;
            if (!meaningfulFor(update, follow)) continue;
            reasons.add(FollowMatcher.reason(follow));
        }
        return reasons.stream().distinct().toList();
    }

    private boolean acceptsPriority(FollowDTO follow, String priority) {
        return switch (follow.notificationLevel()) {
            case "MUTED" -> false;
            case "IMPORTANT" -> "HIGH".equals(priority);
            default -> true;
        };
    }

    private String priorityFor(EventUpdateDTO update) {
        if ("REOPENED".equals(update.type())) return "HIGH";
        if ("REPORT_ADDED".equals(update.type()) && sourceWasAdded(update.metadata())) return "HIGH";
        return "NORMAL";
    }

    private boolean meaningfulFor(EventUpdateDTO update, FollowDTO follow) {
        return switch (update.type()) {
            case "CREATED", "REOPENED" -> true;
            case "REPORT_ADDED" -> "EVENT".equals(follow.type()) || sourceWasAdded(update.metadata());
            case "STATUS_CHANGED" -> "EVENT".equals(follow.type());
            default -> false;
        };
    }

    private boolean sourceWasAdded(Map<String, Object> metadata) {
        return metadata != null && Boolean.TRUE.equals(metadata.get("sourceAdded"));
    }

    private String titleFor(String updateType) {
        return switch (updateType) {
            case "CREATED" -> "New event on your Radar";
            case "REPORT_ADDED" -> "A followed event has a new source";
            case "REOPENED" -> "A followed event is active again";
            case "STATUS_CHANGED" -> "A followed event changed status";
            default -> "Event update";
        };
    }
}
