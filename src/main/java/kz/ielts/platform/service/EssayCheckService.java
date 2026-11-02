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
        WritingTask task = essay.getTask();
        String taskType = task.getTaskType() == null ? null : task.getTaskType().name();
        CriteriaSet set = criteriaSetId != null
                ? criteria.findById(criteriaSetId).orElseThrow(this::notFound)
                : criteria.findActiveFor(task.getOwner().getId(), taskType)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No active criteria set: the teacher must upload criteria first"));

        String system = task.getTaskType() == WritingTask.TaskType.TASK_1
                ? systemPromptTask1(set, task.getModuleType())
                : systemPromptTask2(set);
        String response = ai.check(system, userPrompt(essay));
        JsonNode json = parseJson(response);

        double tr = score(json, "taskResponse");
        double cc = score(json, "coherence");
        double lr = score(json, "lexical");
        double gra = score(json, "grammar");
        String report = json.path("report").asText("Empty report");

        EssayCheck check = new EssayCheck(essay, set, tr, cc, lr, gra, report);
        return checks.save(check);
    }

    /** Общая часть: формат ответа. Ключ taskResponse = TA для Task 1 / TR для Task 2. */
    private static final String JSON_FORMAT = """
            Respond ONLY with valid JSON, no markdown wrapper:
            {
              "taskResponse": <score 1.0-9.0 step 0.5>,
              "coherence": <score>,
              "lexical": <score>,
              "grammar": <score>,
              "report": "<markdown report>"
            }
            """;

    /** Промпт для Writing Task 1: Academic (график/таблица/карта/процесс) или General (письмо). */
    String systemPromptTask1(CriteriaSet set, WritingTask.ModuleType module) {
        String taskSpecific = module == WritingTask.ModuleType.GENERAL
                ? """
                  This is IELTS General Training WRITING TASK 1 — a LETTER (minimum 150 words, 20 minutes).
                  The first criterion is TASK ACHIEVEMENT (TA), not Task Response. For TA check:
                  - the purpose of the letter is clear from the start;
                  - ALL three bullet points of the task are covered and appropriately extended;
                  - the tone/register (formal, semi-formal, informal) is consistent and suits the recipient;
                  - appropriate opening and closing (Dear Sir/Madam ... Yours faithfully, etc.).
                  A letter that misses a bullet point cannot get TA above 5.
                  """
                : """
                  This is IELTS Academic WRITING TASK 1 — a REPORT describing visual information
                  (graph, chart, table, map, process or diagram). Minimum 150 words, 20 minutes.
                  The first criterion is TASK ACHIEVEMENT (TA), not Task Response. For TA check:
                  - there is a clear OVERVIEW of the main trends/stages/differences
                    (no overview ⇒ TA is at most 5);
                  - key features are selected and highlighted, with accurate data and comparisons;
                  - the student does NOT give personal opinions, reasons or speculation;
                  - no conclusion with opinion; no mechanical listing of every number.
                  Coherence: logical grouping of data into paragraphs, accurate linking of comparisons.
                  Lexical resource: range of vocabulary for trends, comparisons and approximations.
                  """;
        return """
                You are an IELTS examiner with 15 years of experience. You are assessing WRITING TASK 1.
                Use ONLY the Task 1 band descriptors. Do not assess it as an essay.

                %s
                === TEACHER'S CRITERIA AND INSTRUCTIONS ===
                %s
                === END OF CRITERIA ===

                %s
                The report field must contain, in English:
                1. The estimated Band Score and the score for each criterion
                   (label the first one "Task Achievement (TA)").
                2. Whether there is a clear overview / whether all bullet points are covered.
                3. Specific errors with quotes from the student's text and corrections.
                4. Improved versions of weak sentences (e.g. better trend/comparison language).
                5. Strengths of the work.
                6. Three priority recommendations for the next Task 1.
                Do not inflate scores. This is a preliminary estimate, not an official IELTS score.
                """.formatted(taskSpecific, set.getContent(), JSON_FORMAT);
    }

    /** Промпт для Writing Task 2: эссе (opinion / discussion / problem-solution / two-part). */
    String systemPromptTask2(CriteriaSet set) {
        return """
                You are an IELTS examiner with 15 years of experience. You are assessing WRITING TASK 2 —
                an ESSAY (minimum 250 words, 40 minutes). Use ONLY the Task 2 band descriptors.

                The first criterion is TASK RESPONSE (TR). For TR check:
                - ALL parts of the question are addressed (e.g. both views + own opinion in a discussion
                  essay; both problems and solutions; both questions in a two-part question);
                - a clear position is presented throughout (for opinion questions);
                - main ideas are extended and supported with relevant reasons and examples;
                - no irrelevant or memorised off-topic content.
                Coherence: clear paragraphing with one central idea per paragraph, logical progression,
                cohesive devices used accurately and not mechanically.
                Lexical resource and grammar: range, accuracy, collocations, complex structures.

                === TEACHER'S CRITERIA AND INSTRUCTIONS ===
                %s
                === END OF CRITERIA ===

                %s
                The report field must contain, in English:
                1. The estimated Band Score and the score for each criterion
                   (label the first one "Task Response (TR)").
                2. Justification of why the essay matches this level; is the position clear?
                3. Specific errors with quotes from the student's text and corrections.
                4. Improved versions of weak sentences.
                5. Strengths of the work.
                6. Three priority recommendations for the next essay.
                Do not inflate scores. This is a preliminary estimate, not an official IELTS score.
                """.formatted(set.getContent(), JSON_FORMAT);
    }

    private String userPrompt(Essay essay) {
        WritingTask task = essay.getTask();
        String imageNote = task.getImageUrl() == null || task.getImageUrl().isBlank() ? "" : """

                Note: the task includes a chart/table image (%s) which you cannot see.
                Judge Task Achievement only by how well the text summarises, selects and compares
                the data as described in the prompt; do not penalise the essay for details
                of the image you cannot verify.""".formatted(task.getImageUrl());
        String label = task.getTaskType() == WritingTask.TaskType.TASK_1 ? "RESPONSE" : "ESSAY";
        return """
                === TASK (%s, %s, minimum words: %d) ===
                %s%s
                === STUDENT'S %s (%d words) ===
                %s
                """.formatted(task.getTaskType(), task.getModuleType(),
                task.getMinWords(), task.getPrompt(), imageNote,
                label, essay.getWordCount(), essay.getText());
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
