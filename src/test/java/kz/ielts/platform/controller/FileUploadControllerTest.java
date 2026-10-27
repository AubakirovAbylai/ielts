package kz.ielts.platform.controller;

import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import kz.ielts.platform.security.JwtService;
import kz.ielts.platform.security.WebConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebConfig.class)
public class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    private static final Path UPLOAD_DIR = Paths.get("data", "uploads");

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        if (Files.exists(UPLOAD_DIR)) {
            try (var stream = Files.list(UPLOAD_DIR)) {
                stream.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private void authenticateAsTeacher() {
        CurrentUser teacher = new CurrentUser(1L, "teacher@example.com", "TEACHER");
        var auth = new UsernamePasswordAuthenticationToken(teacher, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsStudent() {
        CurrentUser student = new CurrentUser(2L, "student@example.com", "STUDENT");
        var auth = new UsernamePasswordAuthenticationToken(student, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void upload_teacher_success() throws Exception {
        authenticateAsTeacher();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/uploads/")));
    }

    @Test
    void upload_student_403() throws Exception {
        authenticateAsStudent();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_wrongExtension() throws Exception {
        authenticateAsTeacher();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes());

        mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_tooLarge() throws Exception {
        authenticateAsTeacher();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isBadRequest());
    }
}
