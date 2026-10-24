package kz.ielts.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.ielts.platform.ai.AiChecker;
import kz.ielts.platform.entity.IeltsBandMath;
import kz.ielts.platform.entity.SpeakingAnswer;
import kz.ielts.platform.entity.SpeakingSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Отчёт ИИ по Speaking-сессии: транскрипции ответов →
 * баллы FC/LR/GRA/P + markdown-отчёт с рекомендациями.
 */
@Service
public class SpeakingReportService {

    private final AiChecker ai;
    private final ObjectMapper mapper = new ObjectMapper();

    public SpeakingReportService(AiChecker ai) {
        this.ai = ai;
    }

    public void generateReport(SpeakingSession session) {
        String response = ai.check(systemPrompt(), userPrompt(session));
        JsonNode json = parse(response);

        double fc = score(json, "fluency");
        double lr = score(json, "lexical");
        double gra = score(json, "grammar");
        double pron = score(json, "pronunciation");
        String report = json.path("report").asText("Отчёт пуст");

        session.applyReport(report, fc, lr, gra, pron);
    }

    private String systemPrompt() {
        return """
                Ты — экзаменатор IELTS Speaking. Тебе даны вопросы и дословные транскрипции
                ответов ученика. Оцени по четырём официальным критериям:

                1. Fluency and Coherence — беглость, связность, длина и развитие ответов.
                2. Lexical Resource — точность и разнообразие лексики, парафраз.
                3. Grammatical Range and Accuracy — времена, структуры, характер ошибок.
                4. Pronunciation — по транскрипции можно оценить лишь косвенно: длина фраз,
                   связки, сигналы самокоррекции; укажи это ограничение в отчёте.

                Отвечай ТОЛЬКО валидным JSON:
                {
                  "fluency": <балл 1.0-9.0 шаг 0.5>,
                  "lexical": <балл>,
                  "grammar": <балл>,
                  "pronunciation": <балл>,
                  "report": "<markdown-отчёт>"
                }

                В отчёте (report): баллы с обоснованием, конкретные фразы ученика с разбором,
                лексические/грамматические ошибки с исправлениями, сильные стороны
                и три приоритетные рекомендации (рекомендации — на русском).
                Не завышай баллы. Оценка предварительная.
                """;
    }

    private String userPrompt(SpeakingSession s) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ТЕМА: %s (%s) ===\n".formatted(
                s.getTopic().getTitle(), s.getPart()));
        for (SpeakingAnswer a : s.getAnswers()) {
            sb.append("\nВопрос: %s\n".formatted(a.getQuestion()));
            sb.append("Ответ ученика (длительность %d сек): %s\n".formatted(
                    a.getDurationSec(), a.getAnswer()));
        }
        return sb.toString();
    }

    private JsonNode parse(String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
            }
            JsonNode node = mapper.readTree(cleaned);
            if (!node.has("report") || !node.has("fluency")) {
                throw new AiChecker.AiUnavailableException("ИИ вернул неполный JSON");
            }
            return node;
        } catch (AiChecker.AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiChecker.AiUnavailableException(
                    "Не удалось разобрать ответ ИИ: " + e.getMessage(), e);
        }
    }

    private double score(JsonNode json, String field) {
        double v = json.path(field).asDouble(Double.NaN);
        if (Double.isNaN(v) || v < 1.0 || v > 9.0) {
            throw new AiChecker.AiUnavailableException("ИИ вернул некорректный балл " + field);
        }
        return IeltsBandMath.round(v);
    }
}
