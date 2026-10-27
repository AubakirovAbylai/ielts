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
        String report = json.path("report").asText("Empty report");

        session.applyReport(report, fc, lr, gra, pron);
    }

    private String systemPrompt() {
        return """
                You are an IELTS Speaking examiner. You are given the questions and verbatim
                transcripts of the student's answers. Assess them against the four official
                criteria:

                1. Fluency and Coherence — fluency, coherence, length and development of answers.
                2. Lexical Resource — accuracy and range of vocabulary, paraphrasing.
                3. Grammatical Range and Accuracy — tenses, structures, nature of errors.
                4. Pronunciation — from a transcript it can only be assessed indirectly:
                   phrase length, linkers, self-correction signals; mention this limitation
                   in the report.

                Respond ONLY with valid JSON:
                {
                  "fluency": <score 1.0-9.0 step 0.5>,
                  "lexical": <score>,
                  "grammar": <score>,
                  "pronunciation": <score>,
                  "report": "<markdown report>"
                }

                In the report field: scores with justification, specific phrases from the
                student with analysis, lexical/grammatical errors with corrections, strengths,
                and three priority recommendations. Write the report in English.
                Do not inflate scores. This is a preliminary estimate.
                """;
    }

    private String userPrompt(SpeakingSession s) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TOPIC: %s (%s) ===\n".formatted(
                s.getTopic().getTitle(), s.getPart()));
        for (SpeakingAnswer a : s.getAnswers()) {
            sb.append("\nPart %s — Question: %s\n".formatted(
                    a.getPart() == null ? "-" : a.getPart(), a.getQuestion()));
            sb.append("Student's answer (duration %d sec): %s\n".formatted(
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
                throw new AiChecker.AiUnavailableException("AI returned incomplete JSON");
            }
            return node;
        } catch (AiChecker.AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiChecker.AiUnavailableException(
                    "Could not parse the AI response: " + e.getMessage(), e);
        }
    }

    private double score(JsonNode json, String field) {
        double v = json.path(field).asDouble(Double.NaN);
        if (Double.isNaN(v) || v < 1.0 || v > 9.0) {
            throw new AiChecker.AiUnavailableException("AI returned an invalid score for " + field);
        }
        return IeltsBandMath.round(v);
    }
}
