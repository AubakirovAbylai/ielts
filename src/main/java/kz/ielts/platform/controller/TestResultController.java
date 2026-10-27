package kz.ielts.platform.controller;

import kz.ielts.platform.entity.IeltsBandMath;
import kz.ielts.platform.entity.TestResult;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.TestResultRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модуль 1: IELTS Progress Tracker.
 * Ученик вносит результаты; Overall считается на сервере по правилам IELTS.
 */
@RestController
@RequestMapping("/api/results")
public class TestResultController {

    private final TestResultRepository results;
    private final UserRepository users;

    public TestResultController(TestResultRepository results, UserRepository users) {
        this.results = results;
        this.users = users;
    }

    public record ResultRequest(LocalDate testDate, String examType,
                                Double listening, Double reading,
                                Double writing, Double speaking,
                                String source, String comment, Long studentId) {}

    /** Плоский DTO результата: без entity User (ленивые связи, passwordHash). */
    public record ResultDto(Long id, LocalDate testDate, String examType,
                            Double listening, Double reading,
                            Double writing, Double speaking,
                            Double overallBand, String source, String comment,
                            String createdAt) {}

    private static ResultDto toDto(TestResult r) {
        return new ResultDto(r.getId(), r.getTestDate(),
                r.getExamType() == null ? null : r.getExamType().name(),
                r.getListening(), r.getReading(), r.getWriting(), r.getSpeaking(),
                r.getOverallBand(), r.getSource(), r.getComment(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    }

    @PostMapping
    public ResultDto add(@RequestBody ResultRequest req, CurrentUser me) {
        if (req.testDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "testDate is required");
        }
        if (req.testDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "testDate cannot be in the future");
        }
        validateSkill("listening", req.listening());
        validateSkill("reading", req.reading());
        validateSkill("writing", req.writing());
        validateSkill("speaking", req.speaking());

        // Учитель может вносить результат за ученика; ученик — только за себя
        User student;
        if (req.studentId() != null && me.isTeacher()) {
            student = users.findById(req.studentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
            if (student.getTeacher() == null || !student.getTeacher().getId().equals(me.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your student");
            }
        } else {
            student = users.findById(me.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }

        TestResult r = new TestResult(student, req.testDate());
        if (req.examType() != null) {
            r.setExamType(TestResult.ExamType.valueOf(req.examType().toUpperCase()));
        }
        r.setListening(req.listening());
        r.setReading(req.reading());
        r.setWriting(req.writing());
        r.setSpeaking(req.speaking());
        r.setSource(req.source());
        r.setComment(req.comment());
        return toDto(results.save(r));
    }

    /** История результатов: своя для ученика, всех учеников — для учителя. */
    @GetMapping
    public List<ResultDto> list(@RequestParam(required = false) Long studentId,
                                 CurrentUser me) {
        if (me.isTeacher()) {
            return (studentId != null
                    ? results.findByStudentIdOrderByTestDateAscIdAsc(studentId)
                    : results.findByStudentTeacherIdOrderByTestDateDesc(me.id()))
                    .stream().map(TestResultController::toDto).toList();
        }
        if (studentId != null && !studentId.equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Students can only see their own results");
        }
        return results.findByStudentIdOrderByTestDateAscIdAsc(me.id())
                .stream().map(TestResultController::toDto).toList();
    }

    /** Сводка прогресса: лучший, последний, целевой, динамика по навыкам. */
    @GetMapping("/progress")
    public Map<String, Object> progress(@RequestParam(required = false) Long studentId,
                                        CurrentUser me) {
        Long target = studentId != null && me.isTeacher() ? studentId : me.id();
        if (!me.isTeacher() && !target.equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Students can only see their own progress");
        }
        List<TestResult> history = results.findByStudentIdOrderByTestDateAscIdAsc(target);

        Double bestOverall = history.stream().map(TestResult::getOverallBand)
                .filter(o -> o != null).mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        TestResult last = history.isEmpty() ? null : history.get(history.size() - 1);
        Double userTarget = users.findById(target).map(User::getTargetBand).orElse(null);

        record Point(Long id, String date, Double listening, Double reading, Double writing,
                     Double speaking, Double overall) {}
        List<Point> series = history.stream()
                .map(r -> new Point(r.getId(), r.getTestDate().toString(), r.getListening(), r.getReading(),
                        r.getWriting(), r.getSpeaking(), r.getOverallBand()))
                .toList();

        Map<String, Object> out = new HashMap<>();
        out.put("count", history.size());
        out.put("bestOverall", Double.isNaN(bestOverall) ? null : bestOverall);
        out.put("lastOverall", last == null || last.getOverallBand() == null ? null : last.getOverallBand());
        out.put("lastDate", last == null ? null : last.getTestDate().toString());
        out.put("targetBand", userTarget);
        out.put("series", series);
        return out;
    }

    /** Экспорт CSV: свои результаты или ученика (учителю). */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public String exportCsv(@RequestParam(required = false) Long studentId, CurrentUser me) {
        List<TestResult> rows = (studentId != null && me.isTeacher())
                ? results.findByStudentIdOrderByTestDateAscIdAsc(studentId)
                : results.findByStudentIdOrderByTestDateAscIdAsc(me.id());
        StringBuilder sb = new StringBuilder(
                "date;examType;listening;reading;writing;speaking;overall;source;comment\n");
        for (TestResult r : rows) {
            sb.append(r.getTestDate()).append(';')
              .append(r.getExamType()).append(';')
              .append(n(r.getListening())).append(';')
              .append(n(r.getReading())).append(';')
              .append(n(r.getWriting())).append(';')
              .append(n(r.getSpeaking())).append(';')
              .append(n(r.getOverallBand())).append(';')
              .append(csv(r.getSource())).append(';')
              .append(csv(r.getComment())).append('\n');
        }
        return sb.toString();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, CurrentUser me) {
        TestResult r = results.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!me.isTeacher() && !r.getStudent().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (me.isTeacher() && !r.getStudent().getTeacher().getId().equals(me.id())
                && !r.getStudent().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        results.delete(r);
    }

    private void validateSkill(String name, Double v) {
        if (!IeltsBandMath.isValidSkillScore(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    name + ": allowed values are 1.0–9.0 in 0.5 steps");
        }
    }

    private static String n(Double v) { return v == null ? "" : String.valueOf(v); }

    private static String csv(String v) {
        return v == null || v.isBlank() ? "" : '"' + v.replace("\"", "\"\"") + '"';
    }
}
