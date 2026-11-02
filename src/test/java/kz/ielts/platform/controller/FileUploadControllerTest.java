package kz.ielts.platform.controller;

import kz.ielts.platform.entity.UploadedImage;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.UploadedImageRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import kz.ielts.platform.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FileUploadControllerTest.MockRepos.class)
public class FileUploadControllerTest {

    /** Локальные моки-репозитории: WebMvcTest не поднимает JPA. */
    @TestConfiguration
    static class MockRepos {
        @Bean UploadedImageRepository images() { return mock(UploadedImageRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadedImageRepository images;

    @Autowired
    private UserRepository users;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(images, users);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsTeacher() {
        CurrentUser teacher = new CurrentUser(1L, "teacher@example.com", "TEACHER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null, List.of()));
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
    }

    private void authenticateAsStudent() {
        CurrentUser student = new CurrentUser(2L, "student@example.com", "STUDENT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(student, null, List.of()));
    }

    private static MockMultipartFile png(byte[] data) {
        return new MockMultipartFile("file", "test.png", "image/png", data);
    }

    @Test
    void upload_teacher_success_savedToDb() throws Exception {
        authenticateAsTeacher();
        byte[] data = {0x01, 0x02, 0x03};
        when(images.save(any(UploadedImage.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(multipart("/api/uploads").file(png(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/uploads/null"));

        ArgumentCaptor<UploadedImage> captor = ArgumentCaptor.forClass(UploadedImage.class);
        verify(images).save(captor.capture());
        assertArrayEquals(data, captor.getValue().getData());
        assertEquals("image/png", captor.getValue().getContentType());
    }

    @Test
    void upload_student_403() throws Exception {
        authenticateAsStudent();
        mockMvc.perform(multipart("/api/uploads").file(png(new byte[]{1})))
                .andExpect(status().isForbidden());
        verify(images, never()).save(any());
    }

    @Test
    void upload_wrongExtension() throws Exception {
        authenticateAsTeacher();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes());
        mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isBadRequest());
        verify(images, never()).save(any());
    }

    @Test
    void upload_tooLarge() throws Exception {
        authenticateAsTeacher();
        mockMvc.perform(multipart("/api/uploads").file(png(new byte[5 * 1024 * 1024 + 1])))
                .andExpect(status().isBadRequest());
        verify(images, never()).save(any());
    }
}
