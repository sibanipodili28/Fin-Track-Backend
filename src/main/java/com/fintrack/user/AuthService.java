package com.fintrack.user;

import com.fintrack.common.BusinessRuleException;
import com.fintrack.config.AppProperties;
import com.fintrack.security.JwtService;
import com.fintrack.user.AuthDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;

    /** Result of a successful authentication: access token goes in the body, refresh token in a cookie. */
    public record Session(String accessToken, String refreshToken, UserDto user) {}

    @Transactional
    public Session register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User u = new User();
        u.setEmail(email);
        u.setFullName(r.fullName().trim());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(Role.USER);
        return issue(users.save(u));
    }

    @Transactional
    public Session login(LoginRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase())
                .filter(User::isEnabled)
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return issue(u);
    }

    /** Rotates the refresh token. Re-use of a revoked token revokes every session of that user. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new BadCredentialsException("Session expired");
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Session expired"));
        if (stored.isRevoked()) {
            refreshTokens.revokeAllForUser(stored.getUserId());
            throw new BadCredentialsException("Session expired");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BadCredentialsException("Session expired");
        }
        User u = users.findById(stored.getUserId()).filter(User::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Session expired"));
        stored.setRevoked(true);
        return issue(u);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    private Session issue(User u) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken t = new RefreshToken();
        t.setUserId(u.getId());
        t.setTokenHash(hash(raw));
        t.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(props.jwt().refreshTokenDays()));
        refreshTokens.save(t);
        return new Session(jwt.generateAccessToken(u), raw, UserDto.from(u));
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
