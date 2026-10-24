package kz.ielts.platform.controller;

import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Профиль: установка целевого балла, смена имени. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository users;

    public ProfileController(UserRepository users) {
        this.users = users;
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
                        "targetBand: 1.0–9.0 с шагом 0.5");
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
