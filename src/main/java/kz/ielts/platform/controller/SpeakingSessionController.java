package kz.ielts.platform.controller;

import kz.ielts.platform.ai.AiChecker;
import kz.ielts.platform.entity.SpeakingAnswer;
import kz.ielts.platform.entity.SpeakingSession;
import kz.ielts.platform.entity.SpeakingTopic;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.SpeakingSessionRepository;
import kz.ielts.platform.repository.SpeakingTopicRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import kz.ielts.platform.service.SpeakingReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модуль 3: сессия практики Speaking.
 * Фронтенд распознаёт речь (Web Speech API) и присылает транскрипции;
 * сервер хранит их и запускает ИИ-отчёт.
 */
@RestController
@RequestMapping("/api/speaking/sessions")
public class SpeakingSessionController {

    private final SpeakingSessionRepository sessions;
    private final SpeakingTopicRepository topics;
    private final UserRepository users;
    private final SpeakingReportService reportService;

    public SpeakingSessionController(SpeakingSessionRepository sessions,
                                     SpeakingTopicRepository topics, UserRepository users,
                                     SpeakingReportService reportService) {
        this.sessions = sessions;
        this.topics = topics;
        this.users = users;
        this.reportService = reportService;
    }

    public record StartRequest(Long topicId, String part) {}

    @PostMapping
    public Map<String, Object> start(@RequestBody StartRequest req, CurrentUser me) {
        User student = users.findById(me.id()).orElseThrow(this::notFound);
        SpeakingTopic topic = topics.findById(req.topicId() == null ? -1 : req.topicId())
                .orElseThrow(this::notFound);
        if (!me.isTeacher()) {
            User teacher = student.getTeacher();
            if (teacher == null || !topic.getOwner().getId().equals(teacher.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This topic is not available");
            }
        }
        SpeakingSession.Part part = SpeakingSession.Part.TOPIC_PRACTICE;
        if (req.part() != null && !req.part().isBlank()) {
            try {
                part = SpeakingSession.Part.valueOf(req.part().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown part: " + req.part());
            }
        }
        // Выбранная часть должна быть заполнена в теме.
        switch (part) {
            case PART_1 -> requireNonEmpty(topic.getPart1Questions(), part);
            case PART_2 -> requireCueCard(topic);
            case PART_3 -> requireNonEmpty(topic.getPart3Questions(), part);
            case FULL_TEST -> {
                requireNonEmpty(topic.getPart1Questions(), part);
                requireCueCard(topic);
                requireNonEmpty(topic.getPart3Questions(), part);
            }
            default -> { /* TOPIC_PRACTICE — проверим только, что тема не пустая */ }
        }
        if (part == SpeakingSession.Part.TOPIC_PRACTICE && topic.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This topic has no questions yet");
        }
        SpeakingSession session = sessions.save(new SpeakingSession(student, topic, part));
        return Map.of("id", session.getId(), "topic", topic.getTitle(), "part", part.name());
    }

    private void requireNonEmpty(List<String> questions, SpeakingSession.Part part) {
        if (questions == null || questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Part " + part.name() + " of this topic is empty");
        }
    }

    private void requireCueCard(SpeakingTopic topic) {
        if (topic.getCueCard() == null || topic.getCueCard().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Part 2 of this topic has no cue card");
        }
    }

    public record AnswerRequest(List<Answer> answers) {}
    public record Answer(String part, String question, String text, Integer durationSec) {}

    /** Завершить сессию: сохранить транскрипции и сгенерировать отчёт. */
    @PostMapping("/{id}/finish")
    public Map<String, Object> finish(@PathVariable Long id, @RequestBody AnswerRequest req,
                                      CurrentUser me) {
        SpeakingSession session = sessions.findById(id).orElseThrow(this::notFound);
        if (!session.getStudent().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (session.getStatus() != SpeakingSession.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This session is already finished");
        }
        if (req.answers() != null) {
            for (Answer a : req.answers()) {
                session.addAnswer(a.part(), a.question(), a.text(), a.durationSec());
            }
        }
        session.markFinished();
        sessions.save(session);

        Map<String, Object> out = new HashMap<>();
        out.put("id", session.getId());
        out.put("status", session.getStatus().name());
        try {
            reportService.generateReport(session);
            sessions.save(session);
            out.put("report", session.getReport());
            out.put("scores", Map.of(
                    "fluency", session.getScoreFluency(),
                    "lexical", session.getScoreLexical(),
                    "grammar", session.getScoreGrammar(),
                    "pronunciation", session.getScorePronunciation(),
                    "overall", session.getScoreOverall()));
        } catch (AiChecker.AiUnavailableException e) {
            out.put("reportError", e.getMessage());
        }
        return out;
    }

    /** Повторная генерация отчёта для завершённой сессии (например, если ИИ был недоступен). */
    @PostMapping("/{id}/report")
    public Map<String, Object> regenerateReport(@PathVariable Long id, CurrentUser me) {
        SpeakingSession session = sessions.findById(id).orElseThrow(this::notFound);
        if (!session.getStudent().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (session.getStatus() != SpeakingSession.Status.FINISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finish the session first");
        }
        if (session.getReport() != null && !session.getReport().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report already exists");
        }
        reportService.generateReport(session);
        sessions.save(session);
        Map<String, Object> out = new HashMap<>();
        out.put("id", session.getId());
        out.put("report", session.getReport());
        out.put("scores", Map.of(
                "fluency", session.getScoreFluency(),
                "lexical", session.getScoreLexical(),
                "grammar", session.getScoreGrammar(),
                "pronunciation", session.getScorePronunciation(),
                "overall", session.getScoreOverall()));
        return out;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) Long studentId,
                                          CurrentUser me) {
        List<SpeakingSession> rows = me.isTeacher()
                ? (studentId != null
                    ? sessions.findByStudentIdOrderByCreatedAtDesc(studentId)
                    : sessions.findByStudentTeacherId(me.id()))
                : sessions.findByStudentIdOrderByCreatedAtDesc(me.id());
        return rows.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("topic", s.getTopic().getTitle());
            m.put("part", s.getPart().name());
            m.put("status", s.getStatus().name());
            m.put("createdAt", s.getCreatedAt().toString());
            m.put("answers", s.getAnswers().size());
            m.put("studentName", s.getStudent().getFullName());
            m.put("overall", s.getScoreOverall());
            return m;
        }).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id, CurrentUser me) {
        SpeakingSession s = sessions.findById(id).orElseThrow(this::notFound);
        if (me.isTeacher()) {
            User teacher = users.findById(me.id()).orElseThrow();
            if (s.getStudent().getTeacher() == null
                    || !s.getStudent().getTeacher().getId().equals(teacher.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else if (!s.getStudent().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("topic", s.getTopic().getTitle());
        m.put("part", s.getPart().name());
        m.put("status", s.getStatus().name());
        m.put("createdAt", s.getCreatedAt().toString());
        m.put("qa", s.getAnswers().stream().map(a -> {
            Map<String, Object> qa = new HashMap<>();
            qa.put("part", a.getPart() == null ? "" : a.getPart());
            qa.put("question", a.getQuestion());
            qa.put("answer", a.getAnswer());
            qa.put("durationSec", a.getDurationSec());
            return qa;
        }).toList());
        m.put("report", s.getReport() == null ? "" : s.getReport());
        m.put("scores", Map.of(
                "fluency", s.getScoreFluency() == null ? 0 : s.getScoreFluency(),
                "lexical", s.getScoreLexical() == null ? 0 : s.getScoreLexical(),
                "grammar", s.getScoreGrammar() == null ? 0 : s.getScoreGrammar(),
                "pronunciation", s.getScorePronunciation() == null ? 0 : s.getScorePronunciation(),
                "overall", s.getScoreOverall() == null ? 0 : s.getScoreOverall()));
        return m;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
