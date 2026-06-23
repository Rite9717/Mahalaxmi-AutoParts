package com.mahalaxmi.autoparts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2:3b}")
    private String ollamaModel;

    public OllamaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<Integer, String> addCompatibility(String partNumber, String partName, String company) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", ollamaModel);
            payload.put("prompt", buildPrompt(partNumber, partName, company));
            payload.put("stream", false);

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<Integer, String> result = new LinkedHashMap<>();
            result.put(response.statusCode(), response.body());
            return result;
        } catch (Exception ex) {
            Map<Integer, String> result = new LinkedHashMap<>();
            result.put(500, ex.getMessage() == null ? "Ollama request failed" : ex.getMessage());
            return result;
        }
    }

    private String buildPrompt(String partNumber, String partName, String company) {
        return """
                Extract vehicle compatibility suggestions for an auto part.
                Company: %s
                Part Number: %s
                Part Name: %s
                Return JSON only.
                """.formatted(company == null ? "" : company, partNumber == null ? "" : partNumber, partName == null ? "" : partName);
    }
}
