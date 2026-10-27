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
 * AI providers. Choice: app.ai.provider = gemini | mock.
 * mock — deterministic stub for local work without a key.
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

    /** Gemini API (free tier): generateContent with JSON response mode. */
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
                headers.set("x-goog-api-key", apiKey); // key in header, not in URL — avoids leaking in logs/errors
                String response = http.postForObject(url, new HttpEntity<>(body.toString(), headers), String.class);
                JsonNode root = mapper.readTree(response);
                JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
                if (!parts.isArray() || parts.isEmpty()) {
                    throw new AiUnavailableException("Empty Gemini response: "
                            + root.path("promptFeedback").toString());
                }
                StringBuilder sb = new StringBuilder();
                for (JsonNode p : parts) sb.append(p.path("text").asText());
                return sb.toString();
            } catch (RestClientException e) {
                throw new AiUnavailableException("Gemini API unavailable: " + e.getMessage(), e);
            } catch (AiUnavailableException e) {
                throw e;
            } catch (Exception e) {
                throw new AiUnavailableException("Could not parse the Gemini response: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Stub: returns a fixed report so everything works without a key.
     * Writing and Speaking expect different response schemas — distinguish by prompt.
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
                          "report": "## Speaking preliminary assessment (mock mode, no Gemini key)\\n\\nYour answers have been recorded and saved. For real assessment set GEMINI_API_KEY and AI_PROVIDER=gemini.\\n\\n**Strengths:** answers are developed.\\n\\n**Recommendations:** set the AI key for real checking."
                        }
                        """;
            }
            return """
                    {
                      "taskResponse": 6.0,
                      "coherence": 6.5,
                      "lexical": 6.0,
                      "grammar": 5.5,
                      "report": "## Preliminary check (mock mode, no Gemini key)\\n\\nThe essay has been received and saved. For real AI checking set the environment variable GEMINI_API_KEY and AI_PROVIDER=gemini.\\n\\n**Strengths:** the text is well structured.\\n\\n**Recommendations:** set the AI key for real checking."
                    }
                    """;
        }
    }
}
