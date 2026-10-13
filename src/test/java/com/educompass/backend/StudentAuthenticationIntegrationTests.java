package com.educompass.backend;

import com.educompass.backend.dto.GoogleAuthRequest;
import com.educompass.backend.dto.LoginRequest;
import com.educompass.backend.dto.RegisterRequest;
import com.educompass.backend.entity.User;
import com.educompass.backend.repository.UserRepository;
import com.educompass.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class StudentAuthenticationIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("1. Successful student registration with email normalization & BCrypt password hashing")
    void testSuccessfulStudentRegistration() throws Exception {
        String testEmail = "unique.student." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest request = new RegisterRequest("Aditya Rao", testEmail.toUpperCase(), "SecretPassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("educompass_token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Aditya Rao")))
                .andExpect(jsonPath("$.email", is(testEmail.toLowerCase())))
                .andExpect(jsonPath("$.role", is("ROLE_STUDENT")))
                .andExpect(jsonPath("$.authProvider", is("LOCAL")))
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        // Verify Database persistence and password hashing
        Optional<User> savedUserOpt = userRepository.findByEmail(testEmail.toLowerCase());
        assertTrue(savedUserOpt.isPresent(), "Student should be saved in users table");
        User savedUser = savedUserOpt.get();

        // Password hash check: must NOT be plaintext
        assertNotEquals("SecretPassword123!", savedUser.getPassword(), "Plaintext password must never be stored");
        assertTrue(passwordEncoder.matches("SecretPassword123!", savedUser.getPassword()), "BCrypt hash must match password");
    }

    @Test
    @DisplayName("2. Duplicate email registration safely rejected with 409 Conflict")
    void testDuplicateEmailRegistration() throws Exception {
        String testEmail = "duplicate.student." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest req1 = new RegisterRequest("First Student", testEmail, "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Attempt second registration with same email
        RegisterRequest req2 = new RegisterRequest("Duplicate Student", testEmail.toUpperCase(), "AnotherPassword123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("3. Validation rejection on invalid input data (short password, invalid email)")
    void testInvalidRegistrationData() throws Exception {
        RegisterRequest invalid = new RegisterRequest("", "invalid-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.errors.name", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()));
    }

    @Test
    @DisplayName("4. Successful student login returning valid JWT and HttpOnly cookie")
    void testSuccessfulLogin() throws Exception {
        String email = "login.test." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest register = new RegisterRequest("Login Student", email, "SecurePassword123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        // Login with valid credentials
        LoginRequest login = new LoginRequest(email.toUpperCase(), "SecurePassword123!", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("educompass_token")))
                .andExpect(jsonPath("$.name", is("Login Student")))
                .andExpect(jsonPath("$.email", is(email.toLowerCase())))
                .andExpect(jsonPath("$.role", is("ROLE_STUDENT")))
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    @DisplayName("5. Login rejection on invalid password or non-existent email (401 Unauthorized)")
    void testInvalidLoginCredentials() throws Exception {
        // Non-existent user
        LoginRequest notFound = new LoginRequest("unknown.student@gmail.com", "WrongPassword123!", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notFound)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid email address or password.")));

        // Existing user with wrong password
        String email = "wrongpass.test." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest register = new RegisterRequest("Pass Student", email, "CorrectPassword123!");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)));

        LoginRequest wrongPass = new LoginRequest(email, "IncorrectPassword!", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPass)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email address or password.")));
    }

    @Test
    @DisplayName("6. Protected access: GET /api/auth/me requires authentication (401 when anonymous)")
    void testUnauthenticatedAccessToMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Protected access: GET /api/auth/me returns student profile with valid JWT Bearer")
    void testAuthenticatedAccessToMeWithToken() throws Exception {
        String email = "profile.test." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest register = new RegisterRequest("Profile Student", email, "Password123!");

        String res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(res).get("token").asText();

        // Call /api/auth/me with Bearer token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Profile Student")))
                .andExpect(jsonPath("$.email", is(email.toLowerCase())))
                .andExpect(jsonPath("$.role", is("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("7b. Protected access: PUT /api/auth/profile updates student name and returns updated profile")
    void testUpdateProfile() throws Exception {
        String email = "update.profile." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest register = new RegisterRequest("Old Name", email, "Password123!");

        String res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(res).get("token").asText();

        // Anonymous update rejected
        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Valid Name\"}"))
                .andExpect(status().isUnauthorized());

        // Invalid name rejected
        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Successful update
        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Valid Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Valid Name")))
                .andExpect(jsonPath("$.email", is(email.toLowerCase())));

        // Verify with GET /api/auth/me
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Valid Name")));
    }

    @Test
    @DisplayName("8. Logout endpoint clears authentication cookie")
    void testLogoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message", is("Logged out successfully.")));
    }

    @Test
    @DisplayName("9. Google token verification properly rejects unconfigured or invalid tokens")
    void testGoogleTokenVerificationRejection() throws Exception {
        GoogleAuthRequest fakeRequest = new GoogleAuthRequest("invalid.jwt.google.id.token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fakeRequest)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 503, "Should reject invalid Google token or unconfigured client");
                });
    }

    @Test
    @DisplayName("10. Public browsing endpoints remain fully accessible without authentication")
    void testPublicBrowsingAPIsAccessible() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pathways"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/careers"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/exams"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/threads"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("11. Logout actively invalidates token, preventing further access to /api/auth/me")
    void testRevokedTokenCannotAccessProtectedEndpoints() throws Exception {
        String email = "revocation.test." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest register = new RegisterRequest("Revoke Student", email, "SecurePass123!");

        String res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(res).get("token").asText();

        // Verify token works before logout
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Logout using Bearer token
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Accessing /api/auth/me with the revoked token must now be rejected (401)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("12. Server-side password complexity rejects weak passwords (400 Bad Request)")
    void testPasswordComplexityEnforcement() throws Exception {
        // Missing uppercase
        RegisterRequest noUpper = new RegisterRequest("Test User", "weak1." + System.currentTimeMillis() + "@gmail.com", "weakpass123!");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noUpper)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least one uppercase letter")));

        // Missing number
        RegisterRequest noDigit = new RegisterRequest("Test User", "weak2." + System.currentTimeMillis() + "@gmail.com", "WeakPassOnly!");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noDigit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least one number")));

        // Missing special char
        RegisterRequest noSpecial = new RegisterRequest("Test User", "weak3." + System.currentTimeMillis() + "@gmail.com", "WeakPass123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSpecial)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least one special character")));
    }

    @Test
    @DisplayName("13. Reference data mutation is restricted: students cannot mutate catalog (403 Forbidden)")
    void testReferenceDataMutationRestrictedToAdmin() throws Exception {
        // Anonymous POST to /api/careers is 401 Unauthorized
        mockMvc.perform(post("/api/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fake Career\"}"))
                .andExpect(status().isUnauthorized());

        // Student (ROLE_STUDENT) attempting to POST /api/careers is 403 Forbidden
        String email = "student.role." + System.currentTimeMillis() + "@gmail.com";
        RegisterRequest register = new RegisterRequest("Student Role", email, "SecurePass123!");
        String res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(res).get("token").asText();

        mockMvc.perform(post("/api/careers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked Career\"}"))
                .andExpect(status().isForbidden());
    }
}

