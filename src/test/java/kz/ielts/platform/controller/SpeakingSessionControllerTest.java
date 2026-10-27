package kz.ielts.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.ielts.platform.entity.SpeakingSession;
import kz.ielts.platform.entity.SpeakingTopic;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.SpeakingSessionRepository;
import kz.ielts.platform.repository.SpeakingTopicRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter;
import kz.ielts.platform.security.JwtService;
import kz.ielts.platform.service.SpeakingReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpeakingSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SpeakingSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SpeakingSessionRepository sessions;

    @MockitoBean
    private SpeakingTopicRepository topics;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SpeakingReportService reportService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    private User teacher;
    private User student;
    private User otherStudent;
    private SpeakingTopic topic;

    @BeforeEach
    void setUp() throws Exception {
        teacher = new User("teacher@example.com", "hash", "Teacher One", User.Role.TEACHER);
        setEntityId(teacher, 1L);

        student = new User("student@example.com", "hash", "Student One", User.Role.STUDENT);
        setEntityId(student, 2L);
        student.setTeacher(teacher);

        otherStudent = new User("other@example.com", "hash", "Other Student", User.Role.STUDENT);
        setEntityId(otherStudent, 3L);

        topic = new SpeakingTopic(teacher, "Education");
        setEntityId(topic, 10L);
        topic.setPart1Questions(List.of("What do you study?", "Why did you choose it?"));
        topic.setCueCard("Describe a subject you enjoy.");
        topic.setPart3Questions(List.of("How important is education?"));
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

    private void loginAsOtherStudent() {
        var auth = new TestingAuthenticationToken(
                new JwtAuthFilter.CurrentUser(3L, "other@example.com", "STUDENT"),
                null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void startTopic_success_teacher() throws Exception {
        loginAsTeacher();
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(topics.findById(10L)).thenReturn(Optional.of(topic));
        when(sessions.save(any(SpeakingSession.class))).thenAnswer(inv -> {
            SpeakingSession s = inv.getArgument(0);
            try {
                setEntityId(s, 100L);
            } catch (Exception ignored) {
            }
            return s;
        });

        var request = new SpeakingSessionController.StartRequest(10L, "PART_1");

        mockMvc.perform(post("/api/speaking/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.topic").value("Education"))
                .andExpect(jsonPath("$.part").value("PART_1"));

        ArgumentCaptor<SpeakingSession> captor = ArgumentCaptor.forClass(SpeakingSession.class);
        verify(sessions).save(captor.capture());
        SpeakingSession saved = captor.getValue();
        assertEquals(teacher, saved.getStudent());
        assertEquals(topic, saved.getTopic());
        assertEquals(SpeakingSession.Part.PART_1, saved.getPart());
    }

    @Test
    void startTopic_success_student() throws Exception {
        loginAsStudent();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(topics.findById(10L)).thenReturn(Optional.of(topic));
        when(sessions.save(any(SpeakingSession.class))).thenAnswer(inv -> {
            SpeakingSession s = inv.getArgument(0);
            try {
                setEntityId(s, 101L);
            } catch (Exception ignored) {
            }
            return s;
        });

        var request = new SpeakingSessionController.StartRequest(10L, "PART_1");

        mockMvc.perform(post("/api/speaking/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.topic").value("Education"))
                .andExpect(jsonPath("$.part").value("PART_1"));

        ArgumentCaptor<SpeakingSession> captor = ArgumentCaptor.forClass(SpeakingSession.class);
        verify(sessions).save(captor.capture());
        SpeakingSession saved = captor.getValue();
        assertEquals(student, saved.getStudent());
        assertEquals(topic, saved.getTopic());
    }

    @Test
    void finish_success() throws Exception {
        loginAsStudent();
        SpeakingSession s = new SpeakingSession(student, topic, SpeakingSession.Part.PART_1);
        setEntityId(s, 1L);
        when(sessions.findById(1L)).thenReturn(Optional.of(s));
        when(sessions.save(any(SpeakingSession.class))).thenAnswer(inv -> inv.getArgument(0));

        doAnswer(inv -> {
            SpeakingSession sess = inv.getArgument(0);
            sess.applyReport("Great job!", 7.0, 7.5, 6.5, 7.0);
            return null;
        }).when(reportService).generateReport(any(SpeakingSession.class));

        var answer = new SpeakingSessionController.Answer("PART_1", "What do you study?", "I study CS", 30);
        var request = new SpeakingSessionController.AnswerRequest(List.of(answer));

        mockMvc.perform(post("/api/speaking/sessions/1/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.report").value("Great job!"))
                .andExpect(jsonPath("$.scores.fluency").value(7.0))
                .andExpect(jsonPath("$.scores.lexical").value(7.5))
                .andExpect(jsonPath("$.scores.grammar").value(6.5))
                .andExpect(jsonPath("$.scores.pronunciation").value(7.0))
                .andExpect(jsonPath("$.scores.overall").value(7.0));

        ArgumentCaptor<SpeakingSession> captor = ArgumentCaptor.forClass(SpeakingSession.class);
        verify(sessions, times(2)).save(captor.capture());
        List<SpeakingSession> savedList = captor.getAllValues();
        assertEquals(2, savedList.size());
        SpeakingSession firstSave = savedList.get(0);
        SpeakingSession secondSave = savedList.get(1);
        assertEquals(1, firstSave.getAnswers().size());
        assertEquals("I study CS", firstSave.getAnswers().get(0).getAnswer());
        assertEquals(SpeakingSession.Status.REPORTED, secondSave.getStatus());
        verify(reportService).generateReport(any(SpeakingSession.class));
    }

    @Test
    void finish_wrongStudent_403() throws Exception {
        loginAsOtherStudent();
        SpeakingSession s = new SpeakingSession(student, topic, SpeakingSession.Part.PART_1);
        setEntityId(s, 1L);
        when(sessions.findById(1L)).thenReturn(Optional.of(s));

        var answer = new SpeakingSessionController.Answer("PART_1", "Q", "A", 10);
        var request = new SpeakingSessionController.AnswerRequest(List.of(answer));

        mockMvc.perform(post("/api/speaking/sessions/1/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(sessions, never()).save(any());
        verify(reportService, never()).generateReport(any());
    }

    @Test
    void finish_alreadyFinished_409() throws Exception {
        loginAsStudent();
        SpeakingSession s = new SpeakingSession(student, topic, SpeakingSession.Part.PART_1);
        setEntityId(s, 1L);
        s.markFinished();
        when(sessions.findById(1L)).thenReturn(Optional.of(s));

        var answer = new SpeakingSessionController.Answer("PART_1", "Q", "A", 10);
        var request = new SpeakingSessionController.AnswerRequest(List.of(answer));

        mockMvc.perform(post("/api/speaking/sessions/1/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This session is already finished"));

        verify(sessions, never()).save(any());
    }

    @Test
    void list_teacher() throws Exception {
        loginAsTeacher();
        SpeakingSession s = new SpeakingSession(student, topic, SpeakingSession.Part.PART_1);
        setEntityId(s, 100L);
        s.markFinished();

        when(sessions.findByStudentTeacherId(1L)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/speaking/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].topic").value("Education"))
                .andExpect(jsonPath("$[0].part").value("PART_1"))
                .andExpect(jsonPath("$[0].status").value("FINISHED"))
                .andExpect(jsonPath("$[0].studentName").value("Student One"))
                .andExpect(jsonPath("$[0].answers").value(0));
    }

    @Test
    void list_student() throws Exception {
        loginAsStudent();
        SpeakingSession s = new SpeakingSession(student, topic, SpeakingSession.Part.PART_1);
        setEntityId(s, 100L);

        when(sessions.findByStudentIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/speaking/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].topic").value("Education"))
                .andExpect(jsonPath("$[0].part").value("PART_1"))
                .andExpect(jsonPath("$[0].studentName").value("Student One"))
                .andExpect(jsonPath("$[0].answers").value(0));
    }
}
