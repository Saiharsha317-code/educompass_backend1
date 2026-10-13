package com.educompass.backend;

import com.educompass.backend.dto.RecommendationRequest;
import com.educompass.backend.entity.Career;
import com.educompass.backend.entity.DiscussionThread;
import com.educompass.backend.entity.EntranceExam;
import com.educompass.backend.entity.User;
import com.educompass.backend.repository.UserRepository;
import com.educompass.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class EduCompassApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private User getOrCreateTestStudent(String email, String name) {
        return userRepository.findByEmail(email.toLowerCase()).orElseGet(() -> {
            User user = new User();
            user.setEmail(email.toLowerCase());
            user.setName(name);
            user.setRole("ROLE_STUDENT");
            user.setAuthProvider("LOCAL");
            user.setPassword("SecretPass123!");
            return userRepository.save(user);
        });
    }

    @Test
    @DisplayName("1. Health Check Endpoint")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", containsString("EduCompass")))
                .andExpect(jsonPath("$.database", containsString("MySQL")));
    }

    @Test
    @DisplayName("2. Pathways Endpoint")
    void testPathwaysList() throws Exception {
        mockMvc.perform(get("/api/pathways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(Iterable.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$[?(@.code == 'MPC')].name").exists())
                .andExpect(jsonPath("$[?(@.code == 'POLYCET')].name").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("3. Career CRUD Lifecycle")
    void testCareerCrudLifecycle() throws Exception {
        Career newCareer = new Career();
        newCareer.setTitle("Robotics Automation Specialist");
        newCareer.setCategory("Engineering & Technology");
        newCareer.setStreamRequired("Intermediate MPC / Polytechnic");
        newCareer.setDescription(
                "Focuses on industrial robotic arms and automated factory assembly.");
        newCareer.setWhoShouldConsider(
                "Students who like hardware programming and robotics.");
        newCareer.setKeySkills("ROS, Python, Mechanical Design");
        newCareer.setEducationRoute(
                "Class 10 -> Intermediate MPC -> B.Tech Mechatronics");
        newCareer.setEligibility("10+2 with MPC");
        newCareer.setEntranceExams("TGEAPCET, JEE Main");
        newCareer.setPossibleCourses("B.Tech Robotics & Automation");
        newCareer.setCareerOpportunities(
                "Robotics Engineer at manufacturing plants");
        newCareer.setGovSeatAndScholarshipInfo(
                "TS ePASS applicable for convenor quota");
        newCareer.setFurtherStudyOptions("M.Tech Robotics");

        String createJson = objectMapper.writeValueAsString(newCareer);

        String responseContent = mockMvc.perform(post("/api/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title",
                        is("Robotics Automation Specialist")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long createdId = objectMapper.readTree(responseContent)
                .get("id").asLong();

        mockMvc.perform(get("/api/careers/" + createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdId.intValue())))
                .andExpect(jsonPath("$.title",
                        is("Robotics Automation Specialist")));

        newCareer.setTitle("Robotics & AI Specialist");

        mockMvc.perform(put("/api/careers/" + createdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCareer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title",
                        is("Robotics & AI Specialist")));

        mockMvc.perform(delete("/api/careers/" + createdId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/careers/" + createdId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("4. Invalid Career Validation")
    void testInvalidCareerValidation() throws Exception {
        Career invalid = new Career();
        String json = objectMapper.writeValueAsString(invalid);

        mockMvc.perform(post("/api/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.errors.title", notNullValue()));
    }

    @Test
    @DisplayName("5. Entrance Exams List, Filter and Search")
    void testEntranceExamsListAndFilter() throws Exception {
        mockMvc.perform(get("/api/exams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(5)));

        mockMvc.perform(get("/api/exams?category=ADMISSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category",
                        everyItem(is("ADMISSION"))));

        mockMvc.perform(get(
                        "/api/exams?category=EMPLOYMENT_RECRUITMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category",
                        everyItem(is("EMPLOYMENT_RECRUITMENT"))));

        mockMvc.perform(get("/api/exams?search=POLYCET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name",
                        containsString("TGPOLYCET")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("6. Exam CRUD Lifecycle")
    void testExamCrudLifecycle() throws Exception {
        EntranceExam exam = new EntranceExam();
        exam.setName("Test Entrance Examination");
        exam.setCategory("ADMISSION");
        exam.setTargetField("General Technical");
        exam.setApplicableStage("After Class 10");
        exam.setEligibility("10th Standard Passed");
        exam.setExamPurpose("Admission into diploma courses");
        exam.setExamPattern("100 Multiple Choice Questions");
        exam.setOfficialLink("https://example.gov.in");
        exam.setVerificationStatus("Official sample");
        exam.setNotes("Verified portal link");

        String json = objectMapper.writeValueAsString(exam);

        String response = mockMvc.perform(post("/api/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name",
                        is("Test Entrance Examination")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long examId = objectMapper.readTree(response)
                .get("id").asLong();

        mockMvc.perform(get("/api/exams/" + examId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name",
                        is("Test Entrance Examination")));

        exam.setName("Updated Test Entrance Examination");

        mockMvc.perform(put("/api/exams/" + examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name",
                        is("Updated Test Entrance Examination")));

        mockMvc.perform(delete("/api/exams/" + examId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/exams/" + examId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. Recommendation Engine")
    void testRecommendationEngine() throws Exception {
        RecommendationRequest request = new RecommendationRequest();
        request.setFavoriteSubjects(
                Arrays.asList("Mathematics", "Physical Science"));
        request.setInterests(
                Arrays.asList("Technology & Coding", "Machines & Robotics"));
        request.setWorkingStyle("practical");
        request.setStrengths(
                Arrays.asList("Problem Solving", "Hands-on Technical"));
        request.setPreferredDuration("MEDIUM_TERM");
        request.setBudgetPreference("PREFER_GOVT_SEATS");

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileSummary", notNullValue()))
                .andExpect(jsonPath("$.topPathways", isA(Iterable.class)))
                .andExpect(jsonPath("$.topPathways.length()",
                        greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.topPathways[0].matchReason",
                        notNullValue()))
                .andExpect(jsonPath("$.strategicAdvice", isA(Iterable.class)))
                .andExpect(jsonPath("$.strategicAdvice.length()",
                        greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("8. Discussion Thread CRUD, Ownership Security, and Authentication Checks")
    void testThreadCrudOperations() throws Exception {
        User student1 = getOrCreateTestStudent("student1.forum@test.com", "Arjun Student");
        User student2 = getOrCreateTestStudent("student2.forum@test.com", "Bhavani Student");

        String token1 = jwtUtil.generateToken(student1, false);
        String token2 = jwtUtil.generateToken(student2, false);

        DiscussionThread thread = new DiscussionThread();
        thread.setTitle("How to prepare for TGPOLYCET Mathematics?");
        thread.setCategory("Polytechnic");
        thread.setContent("Which textbook chapters have the highest weightage in Telangana SSC for Polycet?");

        String json = objectMapper.writeValueAsString(thread);

        // A. Anonymous thread creation attempt must be rejected with 401 Unauthorized
        mockMvc.perform(post("/api/threads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());

        // B. Authenticated student 1 creates thread -> 201 Created
        // Author is set by server from student1.getName()
        String response = mockMvc.perform(post("/api/threads")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.author", is("Arjun Student")))
                .andExpect(jsonPath("$.creatorId", is(student1.getId().intValue())))
                .andExpect(jsonPath("$.replyCount", is(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long threadId = objectMapper.readTree(response).get("id").asLong();

        // C. Public read of the thread without authentication is permitted
        mockMvc.perform(get("/api/threads/" + threadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author", is("Arjun Student")))
                .andExpect(jsonPath("$.title", is("How to prepare for TGPOLYCET Mathematics?")));

        // D. Anonymous reply attempt must be rejected with 401 Unauthorized
        String replyJson = "{\"content\":\"Focus on Quadratic Equations, Trigonometry, and Mensuration first!\"}";
        mockMvc.perform(post("/api/threads/" + threadId + "/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyJson))
                .andExpect(status().isUnauthorized());

        // E. Authenticated student 2 posts a reply -> author is set to student 2
        mockMvc.perform(post("/api/threads/" + threadId + "/replies")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount", is(1)))
                .andExpect(jsonPath("$.replies[0].author", is("Bhavani Student")))
                .andExpect(jsonPath("$.replies[0].creatorId", is(student2.getId().intValue())));

        // F. Student 2 (not the owner) attempts to update student 1's question -> 403 Forbidden
        thread.setTitle("Unauthorized update by another student");
        mockMvc.perform(put("/api/threads/" + threadId)
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(thread)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));

        // G. Student 2 (not the owner) attempts to delete student 1's question -> 403 Forbidden
        mockMvc.perform(delete("/api/threads/" + threadId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));

        // H. Student 1 (owner) updates the question -> 200 OK
        thread.setTitle("How to prepare for TGPOLYCET Mathematics & Physics?");
        mockMvc.perform(put("/api/threads/" + threadId)
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(thread)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("How to prepare for TGPOLYCET Mathematics & Physics?")));

        // I. Student 1 (owner) deletes the question -> 204 No Content
        mockMvc.perform(delete("/api/threads/" + threadId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // J. Verify thread is gone (404 Not Found)
        mockMvc.perform(get("/api/threads/" + threadId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("9. Legacy Sample Threads remain readable and protected from modification")
    void testLegacySeedThreadsProtection() throws Exception {
        User student = getOrCreateTestStudent("seed.test@educompass.local", "Test Student");
        String token = jwtUtil.generateToken(student, false);

        // Fetch all threads to verify seed threads exist and are readable
        mockMvc.perform(get("/api/threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(Iterable.class)));

        // Attempt to update seed thread ID 1 (which has creator == null) with student credentials -> 403 Forbidden
        DiscussionThread hack = new DiscussionThread();
        hack.setTitle("Attempted modification of seed thread");
        hack.setCategory("General");
        hack.setContent("Should be blocked");

        mockMvc.perform(put("/api/threads/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hack)))
                .andExpect(status().isForbidden());

        // Attempt to delete seed thread ID 1 -> 403 Forbidden
        mockMvc.perform(delete("/api/threads/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Attempt to update seed reply ID 1 (which has creator == null) with student credentials -> 403 Forbidden
        String updateSeedReplyJson = "{\"content\":\"Attempted modification of seed reply\"}";
        mockMvc.perform(put("/api/threads/1/replies/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateSeedReplyJson))
                .andExpect(status().isForbidden());

        // Attempt to delete seed reply ID 1 -> 403 Forbidden
        mockMvc.perform(delete("/api/threads/1/replies/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. Thread Reply Management - Ownership, CRUD, and Security Protections")
    void testThreadReplyManagement() throws Exception {
        User studentA = getOrCreateTestStudent("reply.studentA@test.com", "Student Alpha");
        User studentB = getOrCreateTestStudent("reply.studentB@test.com", "Student Beta");

        String tokenA = jwtUtil.generateToken(studentA, false);
        String tokenB = jwtUtil.generateToken(studentB, false);

        // 1. Student A creates Thread 1
        DiscussionThread thread1 = new DiscussionThread();
        thread1.setTitle("Best resources for TGPOLYCET Chemistry");
        thread1.setCategory("Polytechnic");
        thread1.setContent("Can someone suggest the top reference books for Chemistry section?");

        String t1Res = mockMvc.perform(post("/api/threads")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(thread1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long thread1Id = objectMapper.readTree(t1Res).get("id").asLong();

        // 2. Student B creates Thread 2
        DiscussionThread thread2 = new DiscussionThread();
        thread2.setTitle("Intermediate MEC college choices in Hyderabad");
        thread2.setCategory("Intermediate");
        thread2.setContent("Looking for recommendations on junior colleges with good commerce faculty.");

        String t2Res = mockMvc.perform(post("/api/threads")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(thread2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long thread2Id = objectMapper.readTree(t2Res).get("id").asLong();

        // 3. Student A creates a reply on Thread 1
        String replyAJson = "{\"content\":\"Check Telangana SCERT Class 10 Chemistry textbook thoroughly.\"}";
        String postReplyRes = mockMvc.perform(post("/api/threads/" + thread1Id + "/replies")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyAJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount", is(1)))
                .andExpect(jsonPath("$.replies[0].author", is("Student Alpha")))
                .andExpect(jsonPath("$.replies[0].creatorId", is(studentA.getId().intValue())))
                .andExpect(jsonPath("$.replies[0].content", is("Check Telangana SCERT Class 10 Chemistry textbook thoroughly.")))
                .andReturn().getResponse().getContentAsString();

        Long replyAId = objectMapper.readTree(postReplyRes).get("replies").get(0).get("id").asLong();

        // 4. Student B adds a second reply on Thread 1
        String replyBJson = "{\"content\":\"Previous years polycet question papers are also very helpful.\"}";
        mockMvc.perform(post("/api/threads/" + thread1Id + "/replies")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount", is(2)))
                .andExpect(jsonPath("$.replies[1].author", is("Student Beta")))
                .andExpect(jsonPath("$.replies[1].creatorId", is(studentB.getId().intValue())));

        // 5. Anonymous attempt to update replyA -> 401 Unauthorized
        String updateJson = "{\"content\":\"Anonymous updated content\"}";
        mockMvc.perform(put("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isUnauthorized());

        // 6. Anonymous attempt to delete replyA -> 401 Unauthorized
        mockMvc.perform(delete("/api/threads/" + thread1Id + "/replies/" + replyAId))
                .andExpect(status().isUnauthorized());

        // 7. Student B (not owner) attempts to update replyA -> 403 Forbidden
        mockMvc.perform(put("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));

        // 8. Student B (not owner) attempts to delete replyA -> 403 Forbidden
        mockMvc.perform(delete("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));

        // 9. Reply does not belong to specified thread (replyA under thread2) -> 400 Bad Request
        mockMvc.perform(put("/api/threads/" + thread2Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        mockMvc.perform(delete("/api/threads/" + thread2Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        // 10. Nonexistent reply ID -> 404 Not Found
        mockMvc.perform(put("/api/threads/" + thread1Id + "/replies/999999")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));

        mockMvc.perform(delete("/api/threads/" + thread1Id + "/replies/999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));

        // 11. Empty/blank content update -> 400 Bad Request
        String blankUpdateJson = "{\"content\":\"\"}";
        mockMvc.perform(put("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankUpdateJson))
                .andExpect(status().isBadRequest());

        // 12. Student A (creator) successfully updates replyA -> 200 OK
        String validUpdateJson = "{\"content\":\"Updated: Also focus on Organic Chemistry and Chemical Bonding chapters!\"}";
        mockMvc.perform(put("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(replyAId.intValue())))
                .andExpect(jsonPath("$.content", is("Updated: Also focus on Organic Chemistry and Chemical Bonding chapters!")))
                .andExpect(jsonPath("$.creatorId", is(studentA.getId().intValue())));

        // Verify thread get shows updated reply content
        mockMvc.perform(get("/api/threads/" + thread1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount", is(2)))
                .andExpect(jsonPath("$.replies[0].content", is("Updated: Also focus on Organic Chemistry and Chemical Bonding chapters!")));

        // 13. Student A (creator) successfully deletes replyA -> 204 No Content
        mockMvc.perform(delete("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // 14. Verify thread now has replyCount = 1 and replyA is removed
        mockMvc.perform(get("/api/threads/" + thread1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount", is(1)))
                .andExpect(jsonPath("$.replies.length()", is(1)))
                .andExpect(jsonPath("$.replies[0].author", is("Student Beta")));

        // 15. Verify deleting already-deleted replyA -> 404 Not Found
        mockMvc.perform(delete("/api/threads/" + thread1Id + "/replies/" + replyAId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }
}