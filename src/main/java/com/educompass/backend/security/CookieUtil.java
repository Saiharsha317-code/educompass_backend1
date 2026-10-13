package com.educompass.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    public static final String AUTH_COOKIE_NAME = "educompass_token";

    public ResponseCookie createAuthCookie(String token, boolean rememberMe, boolean isSecure) {
        long seconds = rememberMe ? 7 * 24 * 3600 : 24 * 3600;
        return ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(seconds))
                .build();
    }

    public ResponseCookie createLogoutCookie(boolean isSecure) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }

    public String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
