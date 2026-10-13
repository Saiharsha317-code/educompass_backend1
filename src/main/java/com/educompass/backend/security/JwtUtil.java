package com.educompass.backend.security;

import com.educompass.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;
    private final long rememberExpirationMs;

    public static final String DEFAULT_DEV_SECRET = "c7f8a9e1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9";

    public JwtUtil(
            @Value("${app.jwt.secret:c7f8a9e1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${app.jwt.remember-expiration-ms:604800000}") long rememberExpirationMs,
            @Value("${spring.profiles.active:}") String activeProfiles) {

        if (activeProfiles != null && (activeProfiles.contains("prod") || activeProfiles.contains("production"))) {
            if (secret == null || secret.isBlank() || DEFAULT_DEV_SECRET.equals(secret) || secret.length() < 32) {
                throw new IllegalStateException("FATAL: In production profile, JWT_SECRET must be explicitly set with a unique, high-entropy 256-bit secret.");
            }
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.rememberExpirationMs = rememberExpirationMs;
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    public String generateToken(User user, boolean rememberMe) {
        long duration = rememberMe ? rememberExpirationMs : expirationMs;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + duration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("name", user.getName())
                .claim("role", user.getRole())
                .claim("authProvider", user.getAuthProvider())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Number id = extractClaims(token).get("userId", Number.class);
        return id != null ? id.longValue() : null;
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }
}
