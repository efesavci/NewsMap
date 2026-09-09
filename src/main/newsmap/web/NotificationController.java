package main.newsmap.web;

import main.newsmap.web.dto.PersonalizationDTO.NotificationInboxDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationRefreshDTO;
import main.newsmap.web.dto.PersonalizationDTO.DeliveryBundleDTO;
import java.util.List;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final PersonalizationStore store;
    private final NotificationService notifications;

    public NotificationController(PersonalizationStore store, NotificationService notifications) {
        this.store = store;
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationInboxDTO getInbox(
            @RequestParam String userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        return buildInbox(userId, unreadOnly);
    }

    private NotificationInboxDTO buildInbox(String userId, boolean unreadOnly) {
        var items = store.listNotifications(userId, unreadOnly);
        long unread = unreadOnly ? items.size() : items.stream().filter(item -> item.readAt() == null).count();
        return new NotificationInboxDTO(userId, unread, items);
    }

    @PostMapping("/refresh")
    public NotificationRefreshDTO refresh(@RequestParam String userId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        return notifications.refreshUser(userId);
    }

    @GetMapping("/outbox")
    public List<DeliveryBundleDTO> getOutbox(
            @RequestParam String userId,
            @RequestParam(defaultValue = "PENDING") String status,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        return store.listDeliveryBundles(userId, status);
    }

    @PostMapping("/{notificationId}/read")
    public NotificationInboxDTO markRead(
            @PathVariable String notificationId,
            @RequestParam String userId,
            Principal principal
    ) {
        AccountAccess.requireOwner(principal, userId);
        store.markNotificationRead(userId, notificationId);
        return buildInbox(userId, false);
    }

    @PostMapping("/read-all")
    public NotificationInboxDTO markAllRead(@RequestParam String userId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        store.markAllNotificationsRead(userId);
        return buildInbox(userId, false);
    }
}
