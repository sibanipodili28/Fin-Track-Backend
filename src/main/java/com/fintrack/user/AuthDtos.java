package com.fintrack.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotBlank @Size(max = 120) String fullName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserDto(Long id, String email, String fullName, Role role) {
        public static UserDto from(User u) { return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole()); }
    }

    public record AuthResponse(String accessToken, UserDto user) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String fullName) {}
}
