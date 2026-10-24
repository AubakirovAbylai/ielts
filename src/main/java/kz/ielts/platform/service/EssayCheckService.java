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
                            "Нет активного набора критериев: преподаватель должен загрузить критерии"));

        String response = ai.check(systemPrompt(set), userPrompt(essay));
        JsonNode json = parseJson(response);

        double tr = score(json, "taskResponse");
        double cc = score(json, "coherence");
        double lr = score(json, "lexical");
        double gra = score(json, "grammar");
        String report = json.path("report").asText("Отчёт пуст");

        EssayCheck check = new EssayCheck(essay, set, tr, cc, lr, gra, report);
        return checks.save(check);
    }

    private String systemPrompt(CriteriaSet set) {
        return """
                Ты — экзаменатор IELTS с 15-летним стажем. Ты проверяешь эссе строго по критериям
                и методическим указаниям преподавателя, приведённым ниже.

                === КРИТЕРИИ И МЕТОДИКА ПРЕПОДАВАТЕЛЯ ===
                %s
                === КОНЕЦ КРИТЕРИЕВ ===

                Отвечай ТОЛЬКО валидным JSON без markdown-обёртки:
                {
                  "taskResponse": <балл 1.0-9.0 шаг 0.5>,
                  "coherence": <балл>,
                  "lexical": <балл>,
                  "grammar": <балл>,
                  "report": "<markdown-отчёт>"
                }

                Отчёт (поле report) должен содержать на английском:
                1. Предполагаемый Band Score и балл по каждому критерию.
                2. Обоснование, почему эссе соответствует этому уровню.
                3. Конкретные ошибки с цитатами из текста ученика и исправлениями.
                4. Примеры улучшения неудачных предложений.
                5. Сильные стороны работы.
                6. Три приоритетные рекомендации для следующего эссе (на русском).
                Не завышай баллы. Оценка предварительная, не официальный балл IELTS.
                """.formatted(set.getContent());
    }

    private String userPrompt(Essay essay) {
        WritingTask task = essay.getTask();
        return """
                === ЗАДАНИЕ (%s, %s, лимит слов: %d) ===
                %s
                === ЭССЕ УЧЕНИКА (%d слов) ===
                %s
                """.formatted(task.getTaskType(), task.getModuleType(),
                task.getMinWords(), task.getPrompt(),
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
                throw new AiChecker.AiUnavailableException("ИИ вернул неполный JSON");
            }
            return node;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("Не удалось разобрать ответ ИИ: " + e.getMessage(), e);
        }
    }

    private double score(JsonNode json, String field) {
        double v = json.path(field).asDouble(Double.NaN);
        if (Double.isNaN(v) || v < 1.0 || v > 9.0) {
            throw new AiChecker.AiUnavailableException("ИИ вернул некорректный балл " + field);
        }
        return IeltsBandMath.round(v);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Набор критериев не найден");
    }

    public EssayCheck findByEssay(Long essayId) {
        return checks.findByEssayId(essayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Проверка для эссе не найдена"));
    }

    public EssayCheck findByEssayOrNull(Long essayId) {
        return checks.findByEssayId(essayId).orElse(null);
    }

    public EssayCheck save(EssayCheck check) {
        return checks.save(check);
    }
}
