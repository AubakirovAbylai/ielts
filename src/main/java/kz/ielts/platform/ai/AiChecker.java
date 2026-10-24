package kz.ielts.platform.ai;

/**
 * Единый интерфейс ИИ-проверки (ТЗ, раздел 12: смена провайдера без
 * переписывания логики). Вход — эссе + критерии, выход — структурированный отчёт.
 */
public interface AiChecker {

    /**
     * Проверить эссе по критериям.
     * @param systemPrompt системный промпт с критериями преподавателя
     * @param userPrompt   задание + текст эссе + инструкция по формату ответа
     * @return ответ модели: JSON с полями scores и report
     */
    String check(String systemPrompt, String userPrompt);

    /** Запрос к ИИ не удался. */
    class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
        public AiUnavailableException(String message) { super(message); }
    }
}
