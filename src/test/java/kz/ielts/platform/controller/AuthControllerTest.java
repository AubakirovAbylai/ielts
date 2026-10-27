package kz.ielts.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void register_success() throws Exception {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pass");
        when(jwtService.issue(any(), anyString(), anyString())).thenReturn("test-token");

        AuthController.RegisterRequest req = new AuthController.RegisterRequest(
                "new@example.com", "password123", "John Doe");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("test-token"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail() throws Exception {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        AuthController.RegisterRequest req = new AuthController.RegisterRequest(
                "taken@example.com", "password123", "Jane Doe");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shortPassword() throws Exception {
        AuthController.RegisterRequest req = new AuthController.RegisterRequest(
                "short@example.com", "12345", "Short Pass");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_success() throws Exception {
        User user = new User("user@example.com", "hashed-password", "Alice Smith", User.Role.STUDENT);
        user.setId(1L);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-pass", "hashed-password")).thenReturn(true);
        when(jwtService.issue(eq(1L), eq("user@example.com"), eq("STUDENT"))).thenReturn("test-token");

        AuthController.LoginRequest req = new AuthController.LoginRequest(
                "user@example.com", "correct-pass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.targetBand").value(0));
    }

    @Test
    void login_wrongPassword() throws Exception {
        User user = new User("user@example.com", "hashed-password", "Alice Smith", User.Role.STUDENT);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "hashed-password")).thenReturn(false);

        AuthController.LoginRequest req = new AuthController.LoginRequest(
                "user@example.com", "wrong-pass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_userNotFound() throws Exception {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        AuthController.LoginRequest req = new AuthController.LoginRequest(
                "unknown@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
