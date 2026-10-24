package kz.ielts.platform.controller;

import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
                    "email, fullName и пароль (мин. 6 символов) обязательны");
        }
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email уже зарегистрирован");
        }
        // Открытая регистрация — только ученики. Учитель создаётся через /api/admin/teachers
        // или первого пользователя (см. DataSeeder).
        User user = new User(req.email(), encoder.encode(req.password()),
                req.fullName(), User.Role.STUDENT);
        users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("token", jwt.issue(user.getId(), user.getEmail(), "STUDENT"),
                        "role", "STUDENT", "fullName", user.getFullName()));
    }

    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        User user = users.findByEmail(req.email() == null ? "" : req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Неверный email или пароль"));
        if (!encoder.matches(req.password() == null ? "" : req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль");
        }
        return Map.of("token", jwt.issue(user.getId(), user.getEmail(), user.getRole().name()),
                "role", user.getRole().name(),
                "fullName", user.getFullName(),
                "targetBand", user.getTargetBand() == null ? 0 : user.getTargetBand());
    }
}
