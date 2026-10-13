package com.educompass.backend.controller;

import com.educompass.backend.dto.AuthResponse;
import com.educompass.backend.dto.GoogleAuthRequest;
import com.educompass.backend.dto.LoginRequest;
import com.educompass.backend.dto.RegisterRequest;
import com.educompass.backend.dto.UserDto;
import com.educompass.backend.entity.User;
import com.educompass.backend.security.CookieUtil;
import com.educompass.backend.security.JwtUtil;
import com.educompass.backend.security.TokenRevocationService;
import com.educompass.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final TokenRevocationService tokenRevocationService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService,
                          CookieUtil cookieUtil,
                          TokenRevocationService tokenRevocationService,
                          JwtUtil jwtUtil) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
        this.tokenRevocationService = tokenRevocationService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request);
        boolean isSecure = httpRequest.isSecure();
        ResponseCookie cookie = cookieUtil.createAuthCookie(response.getToken(), false, isSecure);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request);
        boolean isSecure = httpRequest.isSecure();
        ResponseCookie cookie = cookieUtil.createAuthCookie(response.getToken(), request.isRememberMe(), isSecure);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.loginWithGoogle(request);
        boolean isSecure = httpRequest.isSecure();
        ResponseCookie cookie = cookieUtil.createAuthCookie(response.getToken(), false, isSecure);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getUserProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserDto> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody com.educompass.backend.dto.UpdateProfileRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.updateProfile(user, request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest httpRequest) {
        String token = resolveToken(httpRequest);
        if (org.springframework.util.StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            java.util.Date exp = jwtUtil.extractExpiration(token);
            tokenRevocationService.revokeToken(token, exp);
        }

        boolean isSecure = httpRequest.isSecure();
        ResponseCookie clearCookie = cookieUtil.createLogoutCookie(isSecure);

        Map<String, String> body = new HashMap<>();
        body.put("message", "Logged out successfully.");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(body);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (org.springframework.util.StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return cookieUtil.extractToken(request);
    }
}
