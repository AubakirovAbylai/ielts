package kz.ielts.platform.controller;

import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Профиль: установка целевого балла, смена имени, смена пароля. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public ProfileController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record PasswordRequest(String currentPassword, String newPassword) {}

    /** Смена своего пароля: нужен текущий пароль, новый — минимум 6 символов. */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody PasswordRequest req, CurrentUser me) {
        User u = users.findById(me.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (req.currentPassword() == null || !encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            // 400, а не 401: фронт на 401 разлогинивает пользователя
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 6 characters");
        }
        if (req.newPassword().equals(req.currentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ from the current one");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
        return Map.of("ok", true);
    }

    public record UpdateRequest(String fullName, Double targetBand) {}

    @GetMapping
    public Map<String, Object> me(CurrentUser user) {
        User u = users.findById(user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("id", u.getId(), "email", u.getEmail(), "fullName", u.getFullName(),
                "role", u.getRole().name(), "targetBand",
                u.getTargetBand() == null ? 0 : u.getTargetBand());
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody UpdateRequest req, CurrentUser me) {
        User u = users.findById(me.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (req.fullName() != null && !req.fullName().isBlank()) u.setFullName(req.fullName());
        if (req.targetBand() != null) {
            if (req.targetBand() < 1.0 || req.targetBand() > 9.0
                    || (req.targetBand() * 2) != Math.floor(req.targetBand() * 2)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "targetBand: 1.0–9.0 in 0.5 steps");
            }
            u.setTargetBand(req.targetBand());
        }
        users.save(u);
        return Map.of("ok", true, "targetBand", u.getTargetBand() == null ? 0 : u.getTargetBand());
    }

    /** Список своих учеников (для учителя) или преподаватель (для ученика). */
    @GetMapping("/people")
    public List<Map<String, Object>> people(CurrentUser me) {
        if (me.isTeacher()) {
            return users.findByTeacherId(me.id()).stream()
                    .map(s -> Map.<String, Object>of("id", s.getId(),
                            "email", s.getEmail(), "fullName", s.getFullName()))
                    .toList();
        }
        User me2 = users.findById(me.id()).orElseThrow();
        return me2.getTeacher() == null ? List.of() :
                List.of(Map.of("id", me2.getTeacher().getId(),
                        "email", me2.getTeacher().getEmail(),
                        "fullName", me2.getTeacher().getFullName()));
    }
}
