package com.educompass.backend.service;

import com.educompass.backend.dto.AuthResponse;
import com.educompass.backend.dto.GoogleAuthRequest;
import com.educompass.backend.dto.LoginRequest;
import com.educompass.backend.dto.RegisterRequest;
import com.educompass.backend.dto.UserDto;
import com.educompass.backend.entity.User;
import com.educompass.backend.exception.DuplicateResourceException;
import com.educompass.backend.repository.UserRepository;
import com.educompass.backend.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GoogleAuthService googleAuthService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       GoogleAuthService googleAuthService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.googleAuthService = googleAuthService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email address already exists.");
        }

        // Validate password requirements (minimum 8 characters, at least 1 uppercase, at least 1 digit, at least 1 special character)
        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        boolean hasUpper = rawPassword.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = rawPassword.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?".indexOf(ch) >= 0);

        if (!hasUpper) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        }
        if (!hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one number.");
        }
        if (!hasSpecial) {
            throw new IllegalArgumentException("Password must contain at least one special character.");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("ROLE_STUDENT");
        user.setAuthProvider("LOCAL");

        User savedUser = userRepository.save(user);
        String token = jwtUtil.generateToken(savedUser, false);

        return new AuthResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getAuthProvider(),
                token,
                "Student account registered successfully."
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email address or password."));

        if ("GOOGLE".equals(user.getAuthProvider()) && user.getPassword() == null) {
            throw new IllegalArgumentException("This account was created via Google Sign-In. Please sign in using Google.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email address or password.");
        }

        String token = jwtUtil.generateToken(user, request.isRememberMe());

        return new AuthResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getAuthProvider(),
                token,
                "Login successful."
        );
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = googleAuthService.verifyToken(request.getIdToken());
        String googleId = payload.getSubject();
        String email = payload.getEmail().toLowerCase().trim();
        String name = (String) payload.get("name");
        if (!StringUtils.hasText(name)) {
            name = email.split("@")[0];
        }

        // 1. Check if user already exists with this Google subject ID
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User existing = byGoogleId.get();
            String token = jwtUtil.generateToken(existing, false);
            return new AuthResponse(
                    existing.getId(),
                    existing.getName(),
                    existing.getEmail(),
                    existing.getRole(),
                    existing.getAuthProvider(),
                    token,
                    "Google sign-in successful."
            );
        }

        // 2. Check if an account already exists with this email
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existingUser = byEmail.get();
            // Prevent silent linking to an unlinked password account
            if ("LOCAL".equals(existingUser.getAuthProvider()) && existingUser.getGoogleId() == null) {
                throw new IllegalArgumentException("An account with this email already exists using password authentication. Please sign in with your email and password.");
            }
        }

        // 3. Register new student via Google
        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setGoogleId(googleId);
        newUser.setAuthProvider("GOOGLE");
        newUser.setRole("ROLE_STUDENT");
        newUser.setPassword(null);

        User savedUser = userRepository.save(newUser);
        String token = jwtUtil.generateToken(savedUser, false);

        return new AuthResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getAuthProvider(),
                token,
                "Student account registered with Google successfully."
        );
    }

    @Transactional(readOnly = true)
    public UserDto getUserProfile(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getAuthProvider(),
                user.getCreatedAt()
        );
    }

    @Transactional
    public UserDto updateProfile(User currentUser, com.educompass.backend.dto.UpdateProfileRequest request) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Please sign in to update profile.");
        }
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new com.educompass.backend.exception.ResourceNotFoundException("User not found"));
        user.setName(request.getName().trim());
        User saved = userRepository.save(user);
        return getUserProfile(saved);
    }
}
