package kz.ielts.platform.controller;

import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record RegisterRequest(String email, String password, String fullName) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().length() < 6
                || req.fullName() == null || req.fullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "email, fullName and password (min. 6 characters) are required");
        }
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered");
        }
        User user = new User(req.email(), encoder.encode(req.password()),
                req.fullName(), User.Role.STUDENT);
        users.save(user);
        log.info("REGISTER OK: email={}, hashLen={}, hashPrefix={}",
                req.email(), user.getPasswordHash().length(),
                user.getPasswordHash().substring(0, Math.min(10, user.getPasswordHash().length())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("token", jwt.issue(user.getId(), user.getEmail(), "STUDENT"),
                        "role", "STUDENT", "fullName", user.getFullName()));
    }

    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        String email = req.email() == null ? "" : req.email();
        String password = req.password() == null ? "" : req.password();

        log.info("LOGIN ATTEMPT: email='{}', password-length={}", email, password.length());

        User user = users.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("LOGIN FAIL: user NOT FOUND for email='{}'", email);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            "Invalid email or password");
                });

        log.info("LOGIN: user found id={}, hashLen={}, hash='{}'",
                user.getId(), user.getPasswordHash().length(), user.getPasswordHash());

        boolean matches = encoder.matches(password, user.getPasswordHash());
        log.info("LOGIN: encoder.matches() = {}, encoder class = {}",
                matches, encoder.getClass().getSimpleName());

        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        log.info("LOGIN OK: email={}, role={}", user.getEmail(), user.getRole());
        return Map.of("token", jwt.issue(user.getId(), user.getEmail(), user.getRole().name()),
                "role", user.getRole().name(),
                "fullName", user.getFullName(),
                "targetBand", user.getTargetBand() == null ? 0 : user.getTargetBand());
    }

    // ===== ВРЕМЕННЫЙ ДИАГНОСТИЧЕСКИЙ ЭНДПОИНТ — УДАЛИТЬ ПОСЛЕ ОТЛАДКИ =====
    @GetMapping("/debug-check")
    public Map<String, Object> debugCheck(@RequestParam(defaultValue = "") String email,
                                          @RequestParam(defaultValue = "") String password) {
        var opt = users.findByEmail(email);
        if (opt.isEmpty()) {
            long totalUsers = users.count();
            return Map.of("error", "user not found", "totalUsersInDb", totalUsers);
        }
        User u = opt.get();
        boolean matches = !password.isEmpty() && encoder.matches(password, u.getPasswordHash());
        return Map.of(
                "email", u.getEmail(),
                "role", u.getRole().name(),
                "hashLength", u.getPasswordHash().length(),
                "hashPrefix", u.getPasswordHash().substring(0, Math.min(15, u.getPasswordHash().length())),
                "encoderClass", encoder.getClass().getName(),
                "matches", matches,
                "totalUsersInDb", users.count()
        );
    }
}
