package com.educompass.backend.security;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenRevocationService {

    private final Map<String, Date> revokedTokens = new ConcurrentHashMap<>();

    public void revokeToken(String token, Date expiration) {
        if (token != null) {
            // If expiration is null, retain for 24 hours
            Date exp = expiration != null ? expiration : new Date(System.currentTimeMillis() + 86400000);
            revokedTokens.put(token, exp);
        }
        cleanupExpired();
    }

    public boolean isRevoked(String token) {
        if (token == null) {
            return false;
        }
        Date expiry = revokedTokens.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.before(new Date())) {
            revokedTokens.remove(token);
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Date now = new Date();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue().before(now));
    }
}
