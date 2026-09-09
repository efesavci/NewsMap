package main.newsmap.web;

import main.newsmap.web.dto.AuthDTO.AccountDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class AccountStore {
    private static final Path DATABASE = Path.of("data/personalization.db");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public AccountStore() {
        try {
            Files.createDirectories(DATABASE.getParent());
            try (Connection connection = open()) {
                connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
                connection.createStatement().executeUpdate("""
                        CREATE TABLE IF NOT EXISTS accounts (
                            user_id TEXT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
                            email TEXT NOT NULL COLLATE NOCASE UNIQUE,
                            password_hash TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            last_login_at TEXT
                        )
                        """);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize account storage", e);
        }
    }

    public synchronized AccountDTO signup(
            String rawEmail,
            String rawPassword,
            String rawDisplayName,
            PasswordEncoder encoder
    ) {
        String email = normalizeEmail(rawEmail);
        String password = validatePassword(rawPassword);
        String displayName = normalizeDisplayName(rawDisplayName);
        String userId = "usr_" + UUID.randomUUID().toString().replace("-", "");
        String now = Instant.now().toString();

        try (Connection connection = open()) {
            connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
            connection.setAutoCommit(false);
            try (PreparedStatement profile = connection.prepareStatement("""
                         INSERT INTO users (user_id, display_name, created_at) VALUES (?, ?, ?)
                         """);
                 PreparedStatement account = connection.prepareStatement("""
                         INSERT INTO accounts (user_id, email, password_hash, created_at, last_login_at)
                         VALUES (?, ?, ?, ?, ?)
                         """)) {
                profile.setString(1, userId);
                profile.setString(2, displayName);
                profile.setString(3, now);
                profile.executeUpdate();

                account.setString(1, userId);
                account.setString(2, email);
                account.setString(3, encoder.encode(password));
                account.setString(4, now);
                account.setString(5, now);
                account.executeUpdate();
                connection.commit();
                return new AccountDTO(userId, email, displayName, now);
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sql && sql.getMessage().toLowerCase(Locale.ROOT).contains("unique")) {
                    throw new IllegalArgumentException("An account with this email already exists");
                }
                throw e;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create account", e);
        }
    }

    public synchronized Optional<AccountDTO> authenticate(
            String rawEmail,
            String rawPassword,
            PasswordEncoder encoder
    ) {
        String email;
        try {
            email = normalizeEmail(rawEmail);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (rawPassword == null) return Optional.empty();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT a.user_id, a.email, a.password_hash, a.created_at, u.display_name
                     FROM accounts a JOIN users u ON u.user_id = a.user_id
                     WHERE a.email = ?
                     """)) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !encoder.matches(rawPassword, result.getString("password_hash"))) {
                    return Optional.empty();
                }
                AccountDTO account = fromResult(result);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE accounts SET last_login_at = ? WHERE user_id = ?")) {
                    update.setString(1, Instant.now().toString());
                    update.setString(2, account.userId());
                    update.executeUpdate();
                }
                return Optional.of(account);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not authenticate account", e);
        }
    }

    public synchronized Optional<AccountDTO> findByUserId(String userId) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT a.user_id, a.email, a.created_at, u.display_name
                     FROM accounts a JOIN users u ON u.user_id = a.user_id
                     WHERE a.user_id = ?
                     """)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(fromResult(result)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read account", e);
        }
    }

    private AccountDTO fromResult(ResultSet result) throws SQLException {
        return new AccountDTO(
                result.getString("user_id"),
                result.getString("email"),
                result.getString("display_name"),
                result.getString("created_at")
        );
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + DATABASE.toAbsolutePath());
    }

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return email;
    }

    private String validatePassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw new IllegalArgumentException("Password must be between 10 and 128 characters");
        }
        return password;
    }

    private String normalizeDisplayName(String rawDisplayName) {
        String displayName = rawDisplayName == null ? "" : rawDisplayName.trim();
        if (displayName.length() < 2 || displayName.length() > 60) {
            throw new IllegalArgumentException("Display name must be between 2 and 60 characters");
        }
        return displayName;
    }
}
