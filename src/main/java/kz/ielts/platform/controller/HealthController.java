package kz.ielts.platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health-check для Render/балансировщика: процесс жив, приложение отвечает.
 * Не ходит в БД и не требует аутентификации.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
