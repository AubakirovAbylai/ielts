package kz.ielts.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Единое тело ошибок: {"message": "..."} — фронт показывает его в тосте.
 * Spring Boot 3 по умолчанию скрывает message из ResponseStatusException.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason()
                : "Ошибка " + e.getStatusCode().value();
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message",
                e.getMessage() == null ? "Некорректный запрос" : e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Внутренняя ошибка сервера"));
    }
}
