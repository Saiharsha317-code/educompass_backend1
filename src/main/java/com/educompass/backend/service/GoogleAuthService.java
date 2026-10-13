package com.educompass.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleAuthService {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    /**
     * Primary Spring-managed constructor. Uses the real Google HTTP transport.
     * Explicitly annotated with @Autowired so Spring Boot 4.x correctly selects
     * this constructor when two public constructors are present.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleAuthService(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId != null ? clientId.trim() : "";
        if (StringUtils.hasText(this.clientId)) {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(this.clientId))
                    .build();
        } else {
            this.verifier = null;
        }
    }

    /**
     * Public test constructor that accepts a pre-built verifier.
     * Intended for unit tests that need to inject a mock verifier without
     * making real HTTP calls to Google's public-key endpoint.
     * Spring will not use this constructor for bean wiring (it uses the
     * @Value-annotated primary constructor instead).
     */
    public GoogleAuthService(String clientId, GoogleIdTokenVerifier verifier) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.verifier = verifier;
    }

    /**
     * Verifies a Google ID token string against Google's public keys.
     *
     * <p>The underlying {@link GoogleIdTokenVerifier} validates:
     * <ul>
     *   <li>Cryptographic signature via Google's JWK endpoint</li>
     *   <li>{@code aud} claim — must match the configured client ID</li>
     *   <li>{@code iss} claim — must be {@code accounts.google.com} or
     *       {@code https://accounts.google.com}</li>
     *   <li>{@code exp} claim — token must not be expired</li>
     * </ul>
     *
     * <p>Additionally this method enforces that the {@code email_verified}
     * claim is {@code true}. An unverified Google email must never be used
     * to locate or create a platform account.
     *
     * @param idTokenString the raw Google ID token JWT string
     * @return the verified token payload
     * @throws IllegalStateException    if Google Sign-In is not configured
     * @throws IllegalArgumentException if the token is invalid, expired,
     *                                  has an audience mismatch, or belongs
     *                                  to an account with an unverified email
     */
    public GoogleIdToken.Payload verifyToken(String idTokenString) {
        if (!StringUtils.hasText(clientId) || this.verifier == null) {
            throw new IllegalStateException("Google Sign-In is not configured on the server. Please configure GOOGLE_CLIENT_ID.");
        }
        if (!StringUtils.hasText(idTokenString)) {
            throw new IllegalArgumentException("Google ID token cannot be empty.");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Failed to verify Google ID token: " + e.getMessage());
        }

        if (idToken == null) {
            throw new IllegalArgumentException("Invalid Google ID token or token has expired / audience mismatch.");
        }

        // Enforce email_verified: a Google account whose email address has not
        // been verified by Google must never be used to claim a platform identity.
        GoogleIdToken.Payload payload = idToken.getPayload();
        Boolean emailVerified = payload.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            throw new IllegalArgumentException("Google account email address is not verified. Sign-in rejected.");
        }

        return payload;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId);
    }

    public String getClientId() {
        return clientId;
    }
}
