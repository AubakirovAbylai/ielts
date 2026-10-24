package kz.ielts.platform.controller;

import kz.ielts.platform.entity.StudentGroup;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.StudentGroupRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Кабинет преподавателя, часть 2: ученики и группы. /api/admin/** — только TEACHER. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final StudentGroupRepository groups;
    private final PasswordEncoder encoder;

    public AdminController(UserRepository users, StudentGroupRepository groups,
                           PasswordEncoder encoder) {
        this.users = users;
        this.groups = groups;
        this.encoder = encoder;
    }

    // ---------- Ученики ----------

    public record CreateStudentRequest(String email, String password, String fullName,
                                       Double targetBand, Long groupId) {}

    @PostMapping("/students")
    public Map<String, Object> createStudent(@RequestBody CreateStudentRequest req, CurrentUser me) {
        if (req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().length() < 6
                || req.fullName() == null || req.fullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "email, fullName и пароль (мин. 6 символов) обязательны");
        }
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email уже занят");
        }
        User teacher = users.findById(me.id()).orElseThrow(this::notFound);
        User student = new User(req.email(), encoder.encode(req.password()),
                req.fullName(), User.Role.STUDENT);
        student.setTeacher(teacher);
        if (req.targetBand() != null) student.setTargetBand(req.targetBand());
        if (req.groupId() != null) {
            StudentGroup g = groups.findById(req.groupId()).orElseThrow(this::notFound);
            if (!g.getTeacher().getId().equals(me.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            student.getGroups().add(g);
        }
        users.save(student);
        return Map.of("id", student.getId(), "email", student.getEmail());
    }

    @GetMapping("/students")
    public List<Map<String, Object>> listStudents(CurrentUser me) {
        return users.findByTeacherId(me.id()).stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("email", s.getEmail());
            m.put("fullName", s.getFullName());
            m.put("targetBand", s.getTargetBand() == null ? 0 : s.getTargetBand());
            m.put("groups", s.getGroups().stream().map(StudentGroup::getName).toList());
            return m;
        }).toList();
    }

    @DeleteMapping("/students/{id}")
    public void deleteStudent(@PathVariable Long id, CurrentUser me) {
        User student = users.findById(id).orElseThrow(this::notFound);
        if (student.getTeacher() == null || !student.getTeacher().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (student.getRole() != User.Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Это не ученик");
        }
        users.delete(student);
    }

    // ---------- Группы ----------

    @PostMapping("/groups")
    public Map<String, Object> createGroup(@RequestBody Map<String, String> req, CurrentUser me) {
        String name = req.get("name");
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name обязателен");
        }
        User teacher = users.findById(me.id()).orElseThrow(this::notFound);
        StudentGroup group = groups.save(new StudentGroup(name.trim(), teacher));
        return Map.of("id", group.getId(), "name", group.getName());
    }

    @GetMapping("/groups")
    public List<Map<String, Object>> listGroups(CurrentUser me) {
        return groups.findByTeacherIdOrderByCreatedAtDesc(me.id()).stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("name", g.getName());
            m.put("students", g.getStudents().stream()
                    .map(s -> Map.of("id", s.getId(), "fullName", s.getFullName())).toList());
            return m;
        }).toList();
    }

    @PutMapping("/groups/{id}/members/{studentId}")
    public void addMember(@PathVariable Long id, @PathVariable Long studentId, CurrentUser me) {
        StudentGroup group = groups.findById(id).orElseThrow(this::notFound);
        if (!group.getTeacher().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        User student = users.findById(studentId).orElseThrow(this::notFound);
        if (student.getTeacher() == null || !student.getTeacher().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш ученик");
        }
        student.getGroups().add(group);
        users.save(student);
    }

    @DeleteMapping("/groups/{id}")
    public void deleteGroup(@PathVariable Long id, CurrentUser me) {
        StudentGroup group = groups.findById(id).orElseThrow(this::notFound);
        if (!group.getTeacher().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        for (User s : List.copyOf(group.getStudents())) {
            s.getGroups().remove(group);
            users.save(s);
        }
        groups.delete(group);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Не найдено");
    }
}
