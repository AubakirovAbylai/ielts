package kz.ielts.platform.controller;

import kz.ielts.platform.ai.AiChecker;
import kz.ielts.platform.entity.Essay;
import kz.ielts.platform.entity.EssayCheck;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.entity.WritingTask;
import kz.ielts.platform.repository.EssayRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.repository.WritingTaskRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import kz.ielts.platform.service.EssayCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модуль 2: эссе ученика + ИИ-проверка + корректировка преподавателем.
 * Античитинг: с эссе приходят метрики набора текста (typingMs, pasteCount).
 */
@RestController
@RequestMapping("/api/essays")
public class EssayController {

    private final EssayRepository essays;
    private final WritingTaskRepository tasks;
    private final UserRepository users;
    private final EssayCheckService checkService;

    public EssayController(EssayRepository essays, WritingTaskRepository tasks,
                           UserRepository users, EssayCheckService checkService) {
        this.essays = essays;
        this.tasks = tasks;
        this.users = users;
        this.checkService = checkService;
    }

    public record SubmitRequest(Long taskId, String text, Long typingMs,
                                Integer pasteCount, Boolean examMode) {}

    /** Ученик отправляет эссе — сразу запускается проверка. */
    @PostMapping
    public Map<String, Object> submit(@RequestBody SubmitRequest req, CurrentUser me) {
        if (req.taskId() == null || req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId и text обязательны");
        }
        User student = users.findById(me.id()).orElseThrow(this::notFound);
        WritingTask task = tasks.findById(req.taskId()).orElseThrow(this::notFound);
        if (!me.isTeacher()) {
            User owner = student.getTeacher() != null ? student.getTeacher() : null;
            if (owner == null || !task.getOwner().getId().equals(owner.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Задание недоступно");
            }
        }

        int attempt = essays.findFirstByStudentIdAndTaskIdOrderByAttemptDesc(me.id(), req.taskId())
                .map(e -> e.getAttempt() + 1).orElse(1);
        Essay essay = new Essay(student, task, req.text().trim(),
                req.typingMs() == null ? 0 : req.typingMs(),
                req.pasteCount() == null ? 0 : req.pasteCount(),
                Boolean.TRUE.equals(req.examMode()), attempt);
        essays.save(essay);

        Map<String, Object> out = new HashMap<>();
        out.put("essayId", essay.getId());
        out.put("wordCount", essay.getWordCount());
        out.put("attempt", attempt);
        out.put("belowMinWords", essay.getWordCount() < task.getMinWords());
        try {
            EssayCheck check = checkService.check(essay, null);
            out.put("check", toDto(check));
        } catch (AiChecker.AiUnavailableException e) {
            out.put("checkError", e.getMessage());
        }
        return out;
    }

    /** История эссе: своя для ученика, всех — для учителя. */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) Long studentId,
                                          CurrentUser me) {
        List<Essay> rows = me.isTeacher()
                ? (studentId != null
                    ? essays.findByStudentIdOrderByCreatedAtDesc(studentId)
                    : essays.findByStudentTeacherId(me.id()))
                : essays.findByStudentIdOrderByCreatedAtDesc(me.id());
        return rows.stream().map(this::summary).toList();
    }

    /** Полные данные одного эссе с отчётом (доступ: автор, его учитель). */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id, CurrentUser me) {
        Essay essay = essays.findById(id).orElseThrow(this::notFound);
        checkAccess(essay, me);
        Map<String, Object> out = summary(essay);
        out.put("text", essay.getText());
        EssayCheck c = checkService.findByEssayOrNull(id);
        if (c != null) {
            out.putAll(toDto(c));
        }
        essays.findFirstByStudentIdAndTaskIdOrderByAttemptDesc(essay.getStudent().getId(),
                        essay.getTask().getId())
                .ifPresent(latest -> out.put("isLatestAttempt", latest.getId().equals(id)));
        return out;
    }

    /** Корректировка оценки преподавателем. */
    public record OverrideRequest(Double taskResponse, Double coherence, Double lexical,
                                  Double grammar, String comment) {}

    @PutMapping("/{id}/override")
    public Map<String, Object> override(@PathVariable Long id,
                                        @RequestBody OverrideRequest req, CurrentUser me) {
        if (!me.isTeacher()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только для преподавателя");
        }
        EssayCheck check = checkService.findByEssay(id);
        if (!check.getEssay().getTask().getOwner().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        check.applyTeacherOverride(
                req.taskResponse() == null ? check.getFinalTaskResponse() : req.taskResponse(),
                req.coherence() == null ? check.getFinalCoherence() : req.coherence(),
                req.lexical() == null ? check.getFinalLexical() : req.lexical(),
                req.grammar() == null ? check.getFinalGrammar() : req.grammar(),
                req.comment());
        checkService.save(check);
        return toDto(check);
    }

    private void checkAccess(Essay essay, CurrentUser me) {
        if (me.isTeacher()) {
            if (!essay.getTask().getOwner().getId().equals(me.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else if (!essay.getStudent().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private Map<String, Object> summary(Essay e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("taskId", e.getTask().getId());
        m.put("taskType", e.getTask().getTaskType().name());
        m.put("studentName", e.getStudent().getFullName());
        m.put("wordCount", e.getWordCount());
        m.put("attempt", e.getAttempt());
        m.put("typingMs", e.getTypingMs());
        m.put("pasteCount", e.getPasteCount());
        m.put("examMode", e.isExamMode());
        m.put("createdAt", e.getCreatedAt().toString());
        EssayCheck c = checkService.findByEssayOrNull(e.getId());
        m.put("checked", c != null);
        if (c != null) m.put("overall", c.getFinalOverall());
        return m;
    }

    private Map<String, Object> toDto(EssayCheck c) {
        Map<String, Object> m = new HashMap<>();
        m.put("ai", Map.of("taskResponse", c.getAiTaskResponse(), "coherence", c.getAiCoherence(),
                "lexical", c.getAiLexical(), "grammar", c.getAiGrammar(), "overall", c.getAiOverall()));
        m.put("final", Map.of("taskResponse", c.getFinalTaskResponse(), "coherence", c.getFinalCoherence(),
                "lexical", c.getFinalLexical(), "grammar", c.getFinalGrammar(), "overall", c.getFinalOverall()));
        m.put("report", c.getReport());
        m.put("criteriaTitle", c.getCriteriaSet().getTitle() + " v" + c.getCriteriaSet().getVersion());
        m.put("teacherComment", c.getTeacherComment() == null ? "" : c.getTeacherComment());
        return m;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Не найдено");
    }
}
