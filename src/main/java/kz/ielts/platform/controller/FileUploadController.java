package kz.ielts.platform.controller;

import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Загрузка картинок для заданий Writing Task 1 (графики/таблицы/диаграммы).
 * Файлы складываются в ./data/uploads и раздаются статикой по /uploads/**.
 */
@RestController
public class FileUploadController {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5 МБ

    private static final Path UPLOAD_DIR = Paths.get("data", "uploads");

    @PostMapping("/api/uploads")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file, CurrentUser me) {
        if (!me.isTeacher()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Teachers only");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PNG, JPG, GIF or WebP images are allowed");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be under 5 MB");
        }
        try {
            Files.createDirectories(UPLOAD_DIR);
            String name = UUID.randomUUID() + "." + ext;
            file.transferTo(UPLOAD_DIR.resolve(name).toAbsolutePath());
            return Map.of("url", "/uploads/" + name);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save the file: " + e.getMessage());
        }
    }
}
