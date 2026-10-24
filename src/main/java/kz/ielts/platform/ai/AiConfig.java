package kz.ielts.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Провайдеры ИИ. Выбор: app.ai.provider = gemini | mock.
 * mock — детерминированная заглушка для локальной работы без ключа.
 */
@Configuration
public class AiConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public AiChecker aiChecker(@Value("${app.ai.provider}") String provider,
                               @Value("${app.ai.gemini.api-key}") String apiKey,
                               @Value("${app.ai.gemini.model}") String model,
                               @Value("${app.ai.gemini.base-url}") String baseUrl,
                               RestTemplate http) {
        if ("gemini".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank()) {
            return new GeminiChecker(http, baseUrl, model, apiKey);
        }
        return new MockChecker();
    }

    /** Gemini API (free tier): generateContent c JSON-режимом ответа. */
    static class GeminiChecker implements AiChecker {
        private final RestTemplate http;
        private final String url;
        private final String apiKey;
        private final ObjectMapper mapper = new ObjectMapper();

        GeminiChecker(RestTemplate http, String baseUrl, String model, String apiKey) {
            this.http = http;
            this.url = baseUrl + "/models/" + model + ":generateContent";
            this.apiKey = apiKey;
        }

        @Override
        public String check(String systemPrompt, String userPrompt) {
            try {
                ObjectNode body = mapper.createObjectNode();
                ObjectNode sysParts = body.putObject("systemInstruction").putArray("parts").addObject();
                sysParts.put("text", systemPrompt);
                ObjectNode userPart = body.putArray("contents").addObject().putArray("parts").addObject();
                userPart.put("text", userPrompt);
                ObjectNode generationConfig = body.putObject("generationConfig");
                generationConfig.put("responseMimeType", "application/json");
                generationConfig.put("temperature", 0.2);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("x-goog-api-key", apiKey); // ключ в заголовке, не в URL — не утекает в логи/ошибки
                String response = http.postForObject(url, new HttpEntity<>(body.toString(), headers), String.class);
                JsonNode root = mapper.readTree(response);
                JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
                if (!parts.isArray() || parts.isEmpty()) {
                    throw new AiUnavailableException("Пустой ответ Gemini: "
                            + root.path("promptFeedback").toString());
                }
                StringBuilder sb = new StringBuilder();
                for (JsonNode p : parts) sb.append(p.path("text").asText());
                return sb.toString();
            } catch (RestClientException e) {
                throw new AiUnavailableException("Gemini API недоступен: " + e.getMessage(), e);
            } catch (AiUnavailableException e) {
                throw e;
            } catch (Exception e) {
                throw new AiUnavailableException("Ошибка разбора ответа Gemini: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Заглушка: возвращает фиксированный отчёт, чтобы всё работало без ключа.
     * Writing и Speaking ожидают разные схемы ответа — различаем по промпту.
     */
    static class MockChecker implements AiChecker {
        @Override
        public String check(String systemPrompt, String userPrompt) {
            boolean speaking = systemPrompt != null
                    && systemPrompt.toLowerCase().contains("speaking");
            if (speaking) {
                return """
                        {
                          "fluency": 6.0,
                          "lexical": 6.0,
                          "grammar": 5.5,
                          "pronunciation": 6.0,
                          "report": "## Предварительная оценка Speaking (МОК-режим, ключ Gemini не задан)\\n\\nОтветы записаны и сохранены. Для реальной оценки задайте GEMINI_API_KEY и AI_PROVIDER=gemini.\\n\\n**Сильные стороны:** ответы развернутые.\\n\\n**Рекомендации:** задайте ключ ИИ для настоящей проверки."
                        }
                        """;
            }
            return """
                    {
                      "taskResponse": 6.0,
                      "coherence": 6.5,
                      "lexical": 6.0,
                      "grammar": 5.5,
                      "report": "## Предварительная проверка (МОК-режим, ключ Gemini не задан)\\n\\nЭссе получено и сохранено. Для реальной ИИ-проверки задайте переменную окружения GEMINI_API_KEY и AI_PROVIDER=gemini.\\n\\n**Сильные стороны:** текст структурирован.\\n\\n**Рекомендации:** задайте ключ ИИ для настоящей проверки."
                    }
                    """;
        }
    }
}
