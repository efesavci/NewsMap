package main.newsmap.web.dto;

import main.newsmap.web.dto.EventFeedDTO.EventDTO;

import java.util.List;

public final class PersonalizationDTO {
    private PersonalizationDTO() {}

    public record FollowDTO(String type, String value, String createdAt, String notificationLevel) {}

    public record ProfileDTO(
            String userId,
            String displayName,
            String createdAt,
            boolean onboardingComplete,
            List<FollowDTO> follows
    ) {}

    public record FollowRequest(String type, String value) {}

    public record OnboardingRequest(List<String> categories) {}

    public record FollowNotificationRequest(String type, String value, String notificationLevel) {}

    public record RadarItemDTO(double score, List<String> reasons, EventDTO event) {}

    public record RadarFeedDTO(
            String userId,
            String generatedAt,
            List<FollowDTO> follows,
            List<RadarItemDTO> items
    ) {}

    public record NotificationDTO(
            String notificationId,
            String userId,
            String eventId,
            String updateId,
            String type,
            String priority,
            String title,
            String body,
            List<String> reasons,
            String eventUpdatedAt,
            String createdAt,
            String readAt
    ) {}

    public record NotificationInboxDTO(
            String userId,
            long unreadCount,
            List<NotificationDTO> notifications
    ) {}

    public record NotificationRefreshDTO(String userId, int created) {}

    public record DeliveryBundleDTO(
            String bundleId,
            String userId,
            String eventId,
            String channel,
            String priority,
            List<String> notificationIds,
            int notificationCount,
            String firstEventAt,
            String lastEventAt,
            String availableAt,
            String status,
            int attemptCount,
            String lastError,
            String createdAt,
            String updatedAt,
            String deliveredAt
    ) {}

    public record NotificationPreferencesDTO(
            String userId,
            boolean deliveryEnabled,
            String minimumPriority,
            boolean quietHoursEnabled,
            String quietStart,
            String quietEnd,
            String timezone
    ) {}

    public record NotificationPreferencesRequest(
            boolean deliveryEnabled,
            String minimumPriority,
            boolean quietHoursEnabled,
            String quietStart,
            String quietEnd,
            String timezone
    ) {}
}
