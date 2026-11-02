package kz.ielts.platform.controller;

import kz.ielts.platform.entity.UploadedImage;
import kz.ielts.platform.repository.UploadedImageRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Загрузка картинок для заданий Writing Task 1 (графики/таблицы/диаграммы).
 * Хранятся в БД (диск Render эфемерный) и отдаются по /uploads/{id}.
 */
@RestController
public class FileUploadController {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5 МБ

    private final UploadedImageRepository images;
    private final UserRepository users;

    public FileUploadController(UploadedImageRepository images, UserRepository users) {
        this.images = images;
        this.users = users;
    }

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
            var owner = users.findById(me.id()).orElseThrow(this::notFound);
            UploadedImage saved = images.save(new UploadedImage(
                    owner, original, contentTypeFor(ext), file.getBytes()));
            return Map.of("url", "/uploads/" + saved.getId());
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save the file: " + e.getMessage());
        }
    }

    /** Отдача картинки: публично (используется в <img> без токена). */
    @GetMapping("/uploads/{id}")
    public ResponseEntity<byte[]> serve(@PathVariable Long id) {
        UploadedImage img = images.findById(id).orElseThrow(this::notFound);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(
                        java.time.Duration.ofDays(30)).cachePublic())
                .body(img.getData());
    }

    private static String contentTypeFor(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg"; // jpg, jpeg
        };
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
