package kz.ielts.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(@Value("${app.jwt.secret}") String configuredSecret,
                      @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(resolveSecret(configuredSecret).getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    /** Секрет из конфига, либо генерируется и сохраняется в ./data/jwt.secret. */
    private String resolveSecret(String configured) {
        if (configured != null && configured.length() >= 32) return configured;
        try {
            Path file = Path.of("data", "jwt.secret");
            if (Files.exists(file)) {
                String saved = Files.readString(file).trim();
                if (saved.length() >= 32) return saved;
            }
            String generated = io.jsonwebtoken.io.Encoders.BASE64.encode(
                    java.security.SecureRandom.getInstanceStrong().generateSeed(48));
            Files.createDirectories(file.getParent());
            Files.writeString(file, generated);
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the JWT secret: " + e.getMessage(), e);
        }
    }

    public String issue(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
