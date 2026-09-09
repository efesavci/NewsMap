package main.newsmap.web;

import main.newsmap.web.dto.PersonalizationDTO.FollowDTO;
import main.newsmap.web.dto.PersonalizationDTO.DeliveryBundleDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationPreferencesDTO;
import main.newsmap.web.dto.PersonalizationDTO.NotificationPreferencesRequest;
import main.newsmap.web.dto.PersonalizationDTO.ProfileDTO;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class PersonalizationStore {
    public record NotificationWriteResult(String notificationId, boolean created, boolean outboxEligible) {}

    private static final Path DATABASE = Path.of("data/personalization.db");
    private static final Set<String> FOLLOW_TYPES = Set.of(
            "CATEGORY", "LOCATION", "ENTITY", "SOURCE", "EVENT"
    );
    private static final Set<String> NOTIFICATION_PRIORITIES = Set.of("NORMAL", "HIGH");
    private static final Set<String> FOLLOW_NOTIFICATION_LEVELS = Set.of("ALL", "IMPORTANT", "MUTED");

    public PersonalizationStore() {
        try {
            Files.createDirectories(DATABASE.getParent());
            try (Connection connection = open()) {
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS users (
                            user_id TEXT PRIMARY KEY,
                            display_name TEXT NOT NULL,
                            created_at TEXT NOT NULL
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS notifications (
                            notification_id TEXT PRIMARY KEY,
                            user_id TEXT NOT NULL REFERENCES users(user_id),
                            event_id TEXT NOT NULL,
                            update_id TEXT NOT NULL,
                            notification_type TEXT NOT NULL,
                            title TEXT NOT NULL,
                            body TEXT NOT NULL,
                            reasons TEXT NOT NULL,
                            event_updated_at TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            read_at TEXT,
                            UNIQUE (user_id, update_id)
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS follows (
                            user_id TEXT NOT NULL REFERENCES users(user_id),
                            follow_type TEXT NOT NULL,
                            value TEXT NOT NULL COLLATE NOCASE,
                            created_at TEXT NOT NULL,
                            PRIMARY KEY (user_id, follow_type, value)
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS notification_preferences (
                            user_id TEXT PRIMARY KEY REFERENCES users(user_id),
                            delivery_enabled INTEGER NOT NULL DEFAULT 1,
                            minimum_priority TEXT NOT NULL DEFAULT 'NORMAL',
                            quiet_hours_enabled INTEGER NOT NULL DEFAULT 0,
                            quiet_start TEXT NOT NULL DEFAULT '22:00',
                            quiet_end TEXT NOT NULL DEFAULT '07:00',
                            timezone TEXT NOT NULL,
                            updated_at TEXT NOT NULL
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
                        ON notifications (user_id, read_at, event_updated_at)
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS notification_outbox (
                            bundle_id TEXT PRIMARY KEY,
                            open_key TEXT UNIQUE,
                            user_id TEXT NOT NULL REFERENCES users(user_id),
                            event_id TEXT NOT NULL,
                            channel TEXT NOT NULL,
                            priority TEXT NOT NULL,
                            notification_ids TEXT NOT NULL,
                            notification_count INTEGER NOT NULL,
                            first_event_at TEXT NOT NULL,
                            last_event_at TEXT NOT NULL,
                            available_at TEXT NOT NULL,
                            status TEXT NOT NULL,
                            attempt_count INTEGER NOT NULL DEFAULT 0,
                            last_error TEXT,
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL,
                            delivered_at TEXT
                        )
                        """);
                connection.createStatement().executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_outbox_ready
                        ON notification_outbox (status, available_at, user_id)
                        """);
                ensureColumn(connection, "notifications", "priority", "TEXT NOT NULL DEFAULT 'NORMAL'");
                ensureColumn(connection, "notifications", "outbox_eligible", "INTEGER NOT NULL DEFAULT 0");
                ensureColumn(connection, "follows", "notification_level", "TEXT NOT NULL DEFAULT 'ALL'");
                ensureColumn(connection, "users", "onboarding_complete", "INTEGER NOT NULL DEFAULT 0");
            }
            ensureUser("local");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize personalization storage", e);
        }
    }

    public synchronized ProfileDTO getProfile(String userId) {
        validateUserId(userId);
        ensureUser(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT display_name, created_at, onboarding_complete FROM users WHERE user_id = ?")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Profile disappeared: " + userId);
                return new ProfileDTO(
                        userId, result.getString("display_name"), result.getString("created_at"),
                        result.getInt("onboarding_complete") == 1,
                        listFollows(connection, userId)
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read profile", e);
        }
    }

    public synchronized ProfileDTO completeOnboarding(String userId, List<String> rawCategories) {
        validateUserId(userId);
        ensureUser(userId);
        List<String> categories = rawCategories == null ? List.of() : rawCategories.stream()
                .map(PersonalizationStore::normalizeValue)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .limit(8)
                .toList();
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement follow = connection.prepareStatement("""
                         INSERT OR IGNORE INTO follows (
                             user_id, follow_type, value, created_at
                         ) VALUES (?, 'CATEGORY', ?, ?)
                         """);
                 PreparedStatement complete = connection.prepareStatement(
                         "UPDATE users SET onboarding_complete = 1 WHERE user_id = ?")) {
                for (String category : categories) {
                    follow.setString(1, userId);
                    follow.setString(2, category);
                    follow.setString(3, Instant.now().toString());
                    follow.addBatch();
                }
                follow.executeBatch();
                complete.setString(1, userId);
                complete.executeUpdate();
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not complete interest onboarding", e);
        }
        return getProfile(userId);
    }

    public synchronized ProfileDTO follow(String userId, String rawType, String rawValue) {
        validateUserId(userId);
        String type = normalizeType(rawType);
        String value = normalizeValue(rawValue);
        ensureUser(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO follows (user_id, follow_type, value, created_at)
                     VALUES (?, ?, ?, ?)
                     """)) {
            statement.setString(1, userId);
            statement.setString(2, type);
            statement.setString(3, value);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not save follow", e);
        }
        return getProfile(userId);
    }

    public synchronized ProfileDTO unfollow(String userId, String rawType, String rawValue) {
        validateUserId(userId);
        String type = normalizeType(rawType);
        String value = normalizeValue(rawValue);
        ensureUser(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM follows WHERE user_id = ? AND follow_type = ? AND value = ?")) {
            statement.setString(1, userId);
            statement.setString(2, type);
            statement.setString(3, value);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not remove follow", e);
        }
        return getProfile(userId);
    }

    public synchronized NotificationWriteResult insertNotification(
            String userId,
            String eventId,
            String updateId,
            String type,
            String priority,
            String title,
            String body,
            List<String> reasons,
            String eventUpdatedAt
    ) {
        validateUserId(userId);
        ensureUser(userId);
        String notificationId = "ntf_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO notifications (
                         notification_id, user_id, event_id, update_id, notification_type,
                         priority, title, body, reasons, event_updated_at, created_at, outbox_eligible
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            statement.setString(1, notificationId);
            statement.setString(2, userId);
            statement.setString(3, eventId);
            statement.setString(4, updateId);
            statement.setString(5, type);
            statement.setString(6, normalizeNotificationPriority(priority));
            statement.setString(7, title);
            statement.setString(8, body);
            statement.setString(9, String.join("\n", reasons));
            statement.setString(10, eventUpdatedAt);
            statement.setString(11, Instant.now().toString());
            boolean created = statement.executeUpdate() == 1;
            if (created) return new NotificationWriteResult(notificationId, true, true);
            try (PreparedStatement existing = connection.prepareStatement("""
                    SELECT notification_id, outbox_eligible FROM notifications
                    WHERE user_id = ? AND update_id = ?
                    """)) {
                existing.setString(1, userId);
                existing.setString(2, updateId);
                try (ResultSet result = existing.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Notification insert was ignored without an existing row");
                    return new NotificationWriteResult(
                            result.getString("notification_id"), false,
                            result.getInt("outbox_eligible") == 1
                    );
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not save notification", e);
        }
    }

    public synchronized void enqueueDeliveryBundle(
            String userId,
            String eventId,
            String notificationId,
            String priority,
            String eventUpdatedAt,
            String availableAt
    ) {
        validateUserId(userId);
        String normalizedPriority = normalizeNotificationPriority(priority);
        String openKey = userId + "|" + eventId + "|DIGEST";
        String now = Instant.now().toString();
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                String existingBundleId = null;
                String existingIds = null;
                String existingPriority = null;
                String existingAvailableAt = null;
                String existingLastEventAt = null;
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT bundle_id, notification_ids, priority, available_at, last_event_at
                        FROM notification_outbox WHERE open_key = ? AND status = 'PENDING'
                        """)) {
                    select.setString(1, openKey);
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) {
                            existingBundleId = result.getString("bundle_id");
                            existingIds = result.getString("notification_ids");
                            existingPriority = result.getString("priority");
                            existingAvailableAt = result.getString("available_at");
                            existingLastEventAt = result.getString("last_event_at");
                        }
                    }
                }

                if (existingBundleId == null) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO notification_outbox (
                                bundle_id, open_key, user_id, event_id, channel, priority,
                                notification_ids, notification_count, first_event_at, last_event_at,
                                available_at, status, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, 'DIGEST', ?, ?, 1, ?, ?, ?, 'PENDING', ?, ?)
                            """)) {
                        insert.setString(1, "out_" + UUID.randomUUID().toString().replace("-", ""));
                        insert.setString(2, openKey);
                        insert.setString(3, userId);
                        insert.setString(4, eventId);
                        insert.setString(5, normalizedPriority);
                        insert.setString(6, notificationId);
                        insert.setString(7, eventUpdatedAt);
                        insert.setString(8, eventUpdatedAt);
                        insert.setString(9, availableAt);
                        insert.setString(10, now);
                        insert.setString(11, now);
                        insert.executeUpdate();
                    }
                } else {
                    List<String> ids = new ArrayList<>(List.of(existingIds.split("\\n")));
                    if (!ids.contains(notificationId)) ids.add(notificationId);
                    String bundledPriority = "HIGH".equals(existingPriority) || "HIGH".equals(normalizedPriority)
                            ? "HIGH" : "NORMAL";
                    String bundledAvailableAt;
                    if ("HIGH".equals(existingPriority) && "HIGH".equals(normalizedPriority)) {
                        bundledAvailableAt = Instant.parse(existingAvailableAt).isAfter(Instant.parse(availableAt))
                                ? existingAvailableAt : availableAt;
                    } else if ("HIGH".equals(existingPriority)) {
                        bundledAvailableAt = existingAvailableAt;
                    } else if ("HIGH".equals(normalizedPriority)) {
                        bundledAvailableAt = availableAt;
                    } else {
                        bundledAvailableAt = Instant.parse(existingAvailableAt).isAfter(Instant.parse(availableAt))
                                ? existingAvailableAt : availableAt;
                    }
                    String bundledLastEventAt = Instant.parse(existingLastEventAt).isAfter(Instant.parse(eventUpdatedAt))
                            ? existingLastEventAt : eventUpdatedAt;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE notification_outbox SET
                                priority = ?, notification_ids = ?, notification_count = ?,
                                last_event_at = ?, available_at = ?, updated_at = ?
                            WHERE bundle_id = ?
                            """)) {
                        update.setString(1, bundledPriority);
                        update.setString(2, String.join("\n", ids));
                        update.setInt(3, ids.size());
                        update.setString(4, bundledLastEventAt);
                        update.setString(5, bundledAvailableAt);
                        update.setString(6, now);
                        update.setString(7, existingBundleId);
                        update.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not enqueue delivery bundle", e);
        }
    }

    public synchronized List<DeliveryBundleDTO> listDeliveryBundles(String userId, String rawStatus) {
        validateUserId(userId);
        ensureUser(userId);
        String status = rawStatus == null ? "PENDING" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "DELIVERED", "FAILED", "ALL").contains(status)) {
            throw new IllegalArgumentException("Outbox status must be PENDING, DELIVERED, FAILED, or ALL");
        }
        String sql = "SELECT * FROM notification_outbox WHERE user_id = ?" +
                ("ALL".equals(status) ? "" : " AND status = ?") +
                " ORDER BY available_at, created_at";
        List<DeliveryBundleDTO> bundles = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            if (!"ALL".equals(status)) statement.setString(2, status);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) bundles.add(deliveryBundleFrom(result));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read delivery outbox", e);
        }
        return bundles;
    }

    public synchronized List<NotificationDTO> listNotifications(String userId, boolean unreadOnly) {
        validateUserId(userId);
        ensureUser(userId);
        String sql = """
                SELECT * FROM notifications WHERE user_id = ?
                """ + (unreadOnly ? " AND read_at IS NULL" : "") +
                " ORDER BY event_updated_at DESC, created_at DESC";
        List<NotificationDTO> notifications = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) notifications.add(notificationFrom(result));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read notifications", e);
        }
        return notifications;
    }

    public synchronized void markNotificationRead(String userId, String notificationId) {
        validateUserId(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE notifications SET read_at = COALESCE(read_at, ?)
                     WHERE notification_id = ? AND user_id = ?
                     """)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, notificationId);
            statement.setString(3, userId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not mark notification read", e);
        }
    }

    public synchronized void markAllNotificationsRead(String userId) {
        validateUserId(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE notifications SET read_at = COALESCE(read_at, ?)
                     WHERE user_id = ?
                     """)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, userId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not mark notifications read", e);
        }
    }

    public synchronized List<String> listUserIds() {
        List<String> users = new ArrayList<>();
        try (Connection connection = open();
             ResultSet result = connection.createStatement().executeQuery("SELECT user_id FROM users")) {
            while (result.next()) users.add(result.getString("user_id"));
        } catch (Exception e) {
            throw new IllegalStateException("Could not list users", e);
        }
        return users;
    }

    public synchronized ProfileDTO updateFollowNotificationLevel(
            String userId,
            String rawType,
            String rawValue,
            String rawLevel
    ) {
        validateUserId(userId);
        String type = normalizeType(rawType);
        String value = normalizeValue(rawValue);
        String level = normalizeFollowNotificationLevel(rawLevel);
        ensureUser(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE follows SET notification_level = ?
                     WHERE user_id = ? AND follow_type = ? AND value = ?
                     """)) {
            statement.setString(1, level);
            statement.setString(2, userId);
            statement.setString(3, type);
            statement.setString(4, value);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Follow does not exist");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not update follow notification level", e);
        }
        return getProfile(userId);
    }

    public synchronized NotificationPreferencesDTO getNotificationPreferences(String userId) {
        validateUserId(userId);
        ensureUser(userId);
        ensureNotificationPreferences(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM notification_preferences WHERE user_id = ?
                     """)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Notification preferences disappeared: " + userId);
                return notificationPreferencesFrom(result);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read notification preferences", e);
        }
    }

    public synchronized NotificationPreferencesDTO updateNotificationPreferences(
            String userId,
            NotificationPreferencesRequest request
    ) {
        validateUserId(userId);
        if (request == null) throw new IllegalArgumentException("Notification preferences are required");
        String priority = normalizeNotificationPriority(request.minimumPriority());
        String quietStart = normalizeTime(request.quietStart(), "quietStart");
        String quietEnd = normalizeTime(request.quietEnd(), "quietEnd");
        String timezone = normalizeTimezone(request.timezone());
        ensureUser(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO notification_preferences (
                         user_id, delivery_enabled, minimum_priority, quiet_hours_enabled,
                         quiet_start, quiet_end, timezone, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(user_id) DO UPDATE SET
                         delivery_enabled = excluded.delivery_enabled,
                         minimum_priority = excluded.minimum_priority,
                         quiet_hours_enabled = excluded.quiet_hours_enabled,
                         quiet_start = excluded.quiet_start,
                         quiet_end = excluded.quiet_end,
                         timezone = excluded.timezone,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, userId);
            statement.setInt(2, request.deliveryEnabled() ? 1 : 0);
            statement.setString(3, priority);
            statement.setInt(4, request.quietHoursEnabled() ? 1 : 0);
            statement.setString(5, quietStart);
            statement.setString(6, quietEnd);
            statement.setString(7, timezone);
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not save notification preferences", e);
        }
        return getNotificationPreferences(userId);
    }

    private void ensureUser(String userId) {
        validateUserId(userId);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO users (user_id, display_name, created_at)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setString(1, userId);
            statement.setString(2, "local".equals(userId) ? "My NewsMap" : userId);
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create profile", e);
        }
    }

    private void ensureNotificationPreferences(String userId) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO notification_preferences (
                         user_id, timezone, updated_at
                     ) VALUES (?, ?, ?)
                     """)) {
            statement.setString(1, userId);
            statement.setString(2, ZoneId.systemDefault().getId());
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create notification preferences", e);
        }
    }

    private List<FollowDTO> listFollows(Connection connection, String userId) throws Exception {
        List<FollowDTO> follows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT follow_type, value, created_at, notification_level FROM follows
                WHERE user_id = ? ORDER BY follow_type, value
                """)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    follows.add(new FollowDTO(
                            result.getString("follow_type"), result.getString("value"),
                            result.getString("created_at"), result.getString("notification_level")
                    ));
                }
            }
        }
        return follows;
    }

    private NotificationDTO notificationFrom(ResultSet result) throws Exception {
        String reasons = result.getString("reasons");
        return new NotificationDTO(
                result.getString("notification_id"), result.getString("user_id"),
                result.getString("event_id"), result.getString("update_id"),
                result.getString("notification_type"), result.getString("priority"),
                result.getString("title"),
                result.getString("body"), reasons.isBlank() ? List.of() : List.of(reasons.split("\\n")),
                result.getString("event_updated_at"), result.getString("created_at"),
                result.getString("read_at")
        );
    }

    private NotificationPreferencesDTO notificationPreferencesFrom(ResultSet result) throws Exception {
        return new NotificationPreferencesDTO(
                result.getString("user_id"),
                result.getInt("delivery_enabled") == 1,
                result.getString("minimum_priority"),
                result.getInt("quiet_hours_enabled") == 1,
                result.getString("quiet_start"),
                result.getString("quiet_end"),
                result.getString("timezone")
        );
    }

    private DeliveryBundleDTO deliveryBundleFrom(ResultSet result) throws Exception {
        String ids = result.getString("notification_ids");
        return new DeliveryBundleDTO(
                result.getString("bundle_id"), result.getString("user_id"),
                result.getString("event_id"), result.getString("channel"),
                result.getString("priority"), ids.isBlank() ? List.of() : List.of(ids.split("\\n")),
                result.getInt("notification_count"), result.getString("first_event_at"),
                result.getString("last_event_at"), result.getString("available_at"),
                result.getString("status"), result.getInt("attempt_count"),
                result.getString("last_error"), result.getString("created_at"),
                result.getString("updated_at"), result.getString("delivered_at")
        );
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + DATABASE.toAbsolutePath());
    }

    private static void ensureColumn(Connection connection, String table, String column, String definition)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) return;
            }
        }
        connection.createStatement().executeUpdate(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition
        );
    }

    private static void validateUserId(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid user ID");
        }
    }

    private static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!FOLLOW_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported follow type");
        return type;
    }

    private static String normalizeValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException("Follow value must be between 1 and 200 characters");
        }
        return normalized;
    }

    private static String normalizeNotificationPriority(String value) {
        String priority = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!NOTIFICATION_PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Minimum priority must be NORMAL or HIGH");
        }
        return priority;
    }

    private static String normalizeFollowNotificationLevel(String value) {
        String level = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!FOLLOW_NOTIFICATION_LEVELS.contains(level)) {
            throw new IllegalArgumentException("Follow notification level must be ALL, IMPORTANT, or MUTED");
        }
        return level;
    }

    private static String normalizeTime(String value, String field) {
        try {
            return LocalTime.parse(value).withSecond(0).withNano(0).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must use HH:mm format");
        }
    }

    private static String normalizeTimezone(String value) {
        try {
            return ZoneId.of(value).getId();
        } catch (Exception e) {
            throw new IllegalArgumentException("Timezone must be a valid IANA zone");
        }
    }
}
