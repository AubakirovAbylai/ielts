package kz.ielts.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.ielts.platform.entity.CriteriaSet;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.entity.WritingTask;
import kz.ielts.platform.repository.CriteriaSetRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.repository.WritingTaskRepository;
import kz.ielts.platform.security.JwtAuthFilter;
import kz.ielts.platform.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WritingController.class)
@AutoConfigureMockMvc(addFilters = false)
class WritingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WritingTaskRepository writingTaskRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CriteriaSetRepository criteriaSetRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    private User teacher;
    private User student;
    private WritingTask task;
    private CriteriaSet criteriaSet;

    @BeforeEach
    void setUp() throws Exception {
        teacher = new User("teacher@example.com", "hash", "Teacher One", User.Role.TEACHER);
        setEntityId(teacher, 1L);

        student = new User("student@example.com", "hash", "Student One", User.Role.STUDENT);
        setEntityId(student, 2L);
        student.setTeacher(teacher);

        task = new WritingTask(teacher, WritingTask.TaskType.TASK_2,
                WritingTask.ModuleType.ACADEMIC, "Describe a memorable journey.");
        setEntityId(task, 10L);
        task.setPromptKind("essay");
        task.setImageUrl("/uploads/task.png");

        criteriaSet = new CriteriaSet(teacher, "Writing Criteria", "## Band 7\n...");
        setEntityId(criteriaSet, 20L);
        criteriaSet.setVersion(1);
        criteriaSet.setActive(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setEntityId(Object entity, Long id) throws Exception {
        java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void loginAsTeacher() {
        var auth = new TestingAuthenticationToken(
                new JwtAuthFilter.CurrentUser(1L, "teacher@example.com", "TEACHER"),
                null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void loginAsStudent() {
        var auth = new TestingAuthenticationToken(
                new JwtAuthFilter.CurrentUser(2L, "student@example.com", "STUDENT"),
                null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ---------- listTasks ----------

    @Test
    void listTasks_teacher() throws Exception {
        loginAsTeacher();
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(writingTaskRepository.findByOwnerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(task));

        mockMvc.perform(get("/api/writing/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].taskType").value("TASK_2"))
                .andExpect(jsonPath("$[0].moduleType").value("ACADEMIC"))
                .andExpect(jsonPath("$[0].promptKind").value("essay"))
                .andExpect(jsonPath("$[0].prompt").value("Describe a memorable journey."))
                .andExpect(jsonPath("$[0].imageUrl").value("/uploads/task.png"))
                .andExpect(jsonPath("$[0].minWords").value(250))
                .andExpect(jsonPath("$[0].timeLimitMin").value(40));
    }

    @Test
    void listTasks_student() throws Exception {
        loginAsStudent();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(writingTaskRepository.findByOwnerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(task));

        mockMvc.perform(get("/api/writing/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].prompt").value("Describe a memorable journey."));
    }

    // ---------- createTask ----------

    @Test
    void createTask() throws Exception {
        loginAsTeacher();
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(writingTaskRepository.save(any(WritingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new WritingController.TaskRequest(
                "TASK_1", "ACADEMIC", "graph",
                "Summarise the information.", "/uploads/chart.png");

        mockMvc.perform(post("/api/writing/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("TASK_1"))
                .andExpect(jsonPath("$.moduleType").value("ACADEMIC"))
                .andExpect(jsonPath("$.promptKind").value("graph"))
                .andExpect(jsonPath("$.prompt").value("Summarise the information."))
                .andExpect(jsonPath("$.imageUrl").value("/uploads/chart.png"))
                .andExpect(jsonPath("$.minWords").value(150))
                .andExpect(jsonPath("$.timeLimitMin").value(20));
    }

    // ---------- active-criteria ----------

    @Test
    void activeCriteria_teacher() throws Exception {
        loginAsTeacher();
        when(criteriaSetRepository.findFirstByOwnerIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.of(criteriaSet));

        mockMvc.perform(get("/api/writing/active-criteria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.title").value("Writing Criteria"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.content").value("## Band 7\n..."))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activeCriteria_student() throws Exception {
        loginAsStudent();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(criteriaSetRepository.findFirstByOwnerIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.of(criteriaSet));

        mockMvc.perform(get("/api/writing/active-criteria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.title").value("Writing Criteria"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activeCriteria_none_404() throws Exception {
        loginAsTeacher();
        when(criteriaSetRepository.findFirstByOwnerIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/writing/active-criteria"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No active criteria set found"));
    }
}
