package main.newsmap.web.dto;

public final class AuthDTO {
    private AuthDTO() {}

    public record AccountDTO(
            String userId,
            String email,
            String displayName,
            String createdAt
    ) {}

    public record SignupRequest(String email, String password, String displayName) {}

    public record LoginRequest(String email, String password) {}
}
