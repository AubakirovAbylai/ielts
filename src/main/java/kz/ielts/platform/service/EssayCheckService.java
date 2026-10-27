package kz.ielts.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.ielts.platform.ai.AiChecker;
import kz.ielts.platform.ai.AiChecker.AiUnavailableException;
import kz.ielts.platform.entity.*;
import kz.ielts.platform.repository.CriteriaSetRepository;
import kz.ielts.platform.repository.EssayCheckRepository;
import kz.ielts.platform.repository.EssayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Проверка эссе: сборка промпта, вызов ИИ, разбор JSON-ответа,
 * сохранение EssayCheck с версией критериев.
 */
@Service
public class EssayCheckService {

    private final AiChecker ai;
    private final EssayRepository essays;
    private final EssayCheckRepository checks;
    private final CriteriaSetRepository criteria;
    private final ObjectMapper mapper = new ObjectMapper();

    public EssayCheckService(AiChecker ai, EssayRepository essays,
                             EssayCheckRepository checks, CriteriaSetRepository criteria) {
        this.ai = ai;
        this.essays = essays;
        this.checks = checks;
        this.criteria = criteria;
    }

    public EssayCheck check(Essay essay, Long criteriaSetId) {
        CriteriaSet set = criteriaSetId != null
                ? criteria.findById(criteriaSetId).orElseThrow(this::notFound)
                : criteria.findFirstByOwnerIdAndActiveTrueOrderByVersionDesc(essay.getTask().getOwner().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No active criteria set: the teacher must upload criteria first"));

        String response = ai.check(systemPrompt(set), userPrompt(essay));
        JsonNode json = parseJson(response);

        double tr = score(json, "taskResponse");
        double cc = score(json, "coherence");
        double lr = score(json, "lexical");
        double gra = score(json, "grammar");
        String report = json.path("report").asText("Empty report");

        EssayCheck check = new EssayCheck(essay, set, tr, cc, lr, gra, report);
        return checks.save(check);
    }

    private String systemPrompt(CriteriaSet set) {
        return """
                You are an IELTS examiner with 15 years of experience. Assess the essay strictly
                against the criteria and the teacher's instructions below.

                === TEACHER'S CRITERIA AND INSTRUCTIONS ===
                %s
                === END OF CRITERIA ===

                Respond ONLY with valid JSON, no markdown wrapper:
                {
                  "taskResponse": <score 1.0-9.0 step 0.5>,
                  "coherence": <score>,
                  "lexical": <score>,
                  "grammar": <score>,
                  "report": "<markdown report>"
                }

                The report field must contain, in English:
                1. The estimated Band Score and the score for each criterion.
                2. Justification of why the essay matches this level.
                3. Specific errors with quotes from the student's text and corrections.
                4. Improved versions of weak sentences.
                5. Strengths of the work.
                6. Three priority recommendations for the next essay.
                Do not inflate scores. This is a preliminary estimate, not an official IELTS score.
                """.formatted(set.getContent());
    }

    private String userPrompt(Essay essay) {
        WritingTask task = essay.getTask();
        String imageNote = task.getImageUrl() == null || task.getImageUrl().isBlank() ? "" : """

                Note: the task includes a chart/table image (%s) which you cannot see.
                Judge Task Response only by how well the text summarises, selects and compares
                the data as described in the prompt; do not penalise the essay for details
                of the image you cannot verify.""".formatted(task.getImageUrl());
        return """
                === TASK (%s, %s, minimum words: %d) ===
                %s%s
                === STUDENT'S ESSAY (%d words) ===
                %s
                """.formatted(task.getTaskType(), task.getModuleType(),
                task.getMinWords(), task.getPrompt(), imageNote,
                essay.getWordCount(), essay.getText());
    }

    private JsonNode parseJson(String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
            }
            JsonNode node = mapper.readTree(cleaned);
            if (!node.has("report") || !node.has("taskResponse")) {
                throw new AiChecker.AiUnavailableException("AI returned incomplete JSON");
            }
            return node;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("Could not parse the AI response: " + e.getMessage(), e);
        }
    }

    private double score(JsonNode json, String field) {
        double v = json.path(field).asDouble(Double.NaN);
        if (Double.isNaN(v) || v < 1.0 || v > 9.0) {
            throw new AiChecker.AiUnavailableException("AI returned an invalid score for " + field);
        }
        return IeltsBandMath.round(v);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Criteria set not found");
    }

    public EssayCheck findByEssay(Long essayId) {
        return checks.findByEssayId(essayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Check for this essay not found"));
    }

    public EssayCheck findByEssayOrNull(Long essayId) {
        return checks.findByEssayId(essayId).orElse(null);
    }

    public EssayCheck save(EssayCheck check) {
        return checks.save(check);
    }
}
