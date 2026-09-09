package main.newsmap.web;

import main.newsmap.web.dto.PersonalizationDTO.FollowRequest;
import main.newsmap.web.dto.PersonalizationDTO.FollowNotificationRequest;
import main.newsmap.web.dto.PersonalizationDTO.NotificationPreferencesDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationPreferencesRequest;
import main.newsmap.web.dto.PersonalizationDTO.ProfileDTO;
import main.newsmap.web.dto.PersonalizationDTO.OnboardingRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
public class PersonalizationController {
    private final PersonalizationStore store;
    private final NotificationService notifications;

    public PersonalizationController(PersonalizationStore store, NotificationService notifications) {
        this.store = store;
        this.notifications = notifications;
    }

    @GetMapping("/{userId}")
    public ProfileDTO getProfile(@PathVariable String userId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        return store.getProfile(userId);
    }

    @PostMapping("/{userId}/follows")
    public ProfileDTO follow(@PathVariable String userId, @RequestBody FollowRequest request, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        ProfileDTO profile = store.follow(userId, request.type(), request.value());
        notifications.refreshUser(userId);
        return profile;
    }

    @PostMapping("/{userId}/onboarding")
    public ProfileDTO completeOnboarding(
            @PathVariable String userId,
            @RequestBody OnboardingRequest request,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        ProfileDTO profile = store.completeOnboarding(userId, request == null ? null : request.categories());
        notifications.refreshUser(userId);
        return profile;
    }

    @DeleteMapping("/{userId}/follows")
    public ProfileDTO unfollow(
            @PathVariable String userId,
            @RequestParam String type,
            @RequestParam String value,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        return store.unfollow(userId, type, value);
    }

    @PutMapping("/{userId}/follows/notification-level")
    public ProfileDTO updateFollowNotificationLevel(
            @PathVariable String userId,
            @RequestBody FollowNotificationRequest request,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        return store.updateFollowNotificationLevel(
                userId, request.type(), request.value(), request.notificationLevel()
        );
    }

    @GetMapping("/{userId}/notification-preferences")
    public NotificationPreferencesDTO getNotificationPreferences(@PathVariable String userId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        return store.getNotificationPreferences(userId);
    }

    @PutMapping("/{userId}/notification-preferences")
    public NotificationPreferencesDTO updateNotificationPreferences(
            @PathVariable String userId,
            @RequestBody NotificationPreferencesRequest request,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        return store.updateNotificationPreferences(userId, request);
    }
}
