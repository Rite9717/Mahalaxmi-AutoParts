package com.mahalaxmi.autoparts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mahalaxmi.autoparts.api.Dtos;
import com.mahalaxmi.autoparts.domain.Part;
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

    public OllamaService(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

//    public Map<Integer, String> addCompatibility(String partNumber, String partName, String company) {
//        try {
//            Map<String, Object> payload = new LinkedHashMap<>();
//            payload.put("model", ollamaModel);
//            payload.put("prompt", buildPrompt(partNumber, partName, company));
//            payload.put("stream", false);
//
//            String body = objectMapper.writeValueAsString(payload);
//            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/generate"))
//                    .timeout(Duration.ofSeconds(120))
//                    .header("Content-Type", "application/json")
//                    .POST(HttpRequest.BodyPublishers.ofString(body))
//                    .build();
//            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//            Map<Integer, String> result = new LinkedHashMap<>();
//            result.put(response.statusCode(), response.body());
//            return result;
//        } catch (Exception ex) {
//            Map<Integer, String> result = new LinkedHashMap<>();
//            result.put(500, ex.getMessage() == null ? "Ollama request failed" : ex.getMessage());
//            return result;
//        }
//    }

    public List<Dtos.CompatibilityAiSuggestion> suggestCompatibility(Part part, String sourceText)
    {
        if (sourceText == null || sourceText.isBlank()) {
            return List.of();
        }
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", ollamaModel);
            payload.put("prompt", buildPrompt(part, sourceText));
            payload.put("stream", false);

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama request failed with status " + response.statusCode());
            }
            return parseSuggestions(response.body(), sourceText);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "Ollama request failed" : ex.getMessage(), ex);
        }
    }

    private List<Dtos.CompatibilityAiSuggestion> parseSuggestions(String responseBody, String sourceText) throws Exception {
        JsonNode ollamaResponse = objectMapper.readTree(responseBody);
        String generatedText = ollamaResponse.path("response").asText("");
        String generatedJson = extractJson(generatedText);
        if (generatedJson.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(generatedJson);
        JsonNode models = root.path("models");
        if (!models.isArray()) {
            return List.of();
        }

        List<Dtos.CompatibilityAiSuggestion> suggestions = new ArrayList<>();
        for (JsonNode model : models) {
            String brand = safe(model.path("brand").asText(""));
            String modelName = safe(model.path("model").asText(""));
            String source = safe(model.path("source").asText(""));
            if (brand.isBlank() || modelName.isBlank() || !hasUsableSource(source) || isNegatedModel(sourceText, modelName)) {
                continue;
            }
            String series = normalizeSeriesForSource(model.path("series").asText(""), source);
            ModelSeries normalized = normalizeModelSeries(modelName, series);
            if (normalized.modelName().isBlank() || normalized.series().isBlank()) {
                continue;
            }
            suggestions.add(new Dtos.CompatibilityAiSuggestion(
                    brand,
                    normalized.modelName(),
                    normalized.series(),
                    safe(model.path("confidence").asText("")),
                    source
            ));
        }
        return suggestions;
    }

    private boolean hasUsableSource(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String lower = source.trim().toLowerCase();
        return lower.contains("marutisuzuki.com") || lower.contains("boodmo.com");
    }

    private boolean isNegatedModel(String sourceText, String modelName) {
        String source = safe(sourceText).toLowerCase();
        String model = safe(modelName).toLowerCase()
                .replace("swift dzire", "dzire")
                .replaceAll("\\s+type\\s+\\d+", "")
                .trim();
        if (source.isBlank() || model.isBlank()) {
            return false;
        }
        return source.contains("does not fit maruti suzuki " + model)
                || source.contains("does not fit " + model)
                || source.contains("not fit maruti suzuki " + model)
                || source.contains("not fit " + model)
                || source.contains("not compatible with maruti suzuki " + model)
                || source.contains("not compatible with " + model);
    }

    private ModelSeries normalizeModelSeries(String modelName, String series) {
        String cleanModel = safe(modelName);
        String cleanSeries = normalizeSeries(series);
        if (cleanModel.equalsIgnoreCase("STANDARD")
                || cleanModel.equalsIgnoreCase("STD")
                || cleanModel.equalsIgnoreCase("UNIVERSAL")
                || cleanModel.equalsIgnoreCase("ALL")
                || cleanModel.equalsIgnoreCase("MARUTI SUZUKI")) {
            return new ModelSeries("", "");
        }
        String upperModel = cleanModel.toUpperCase();
        for (int type = 1; type <= 4; type++) {
            String marker = "TYPE " + type;
            if (upperModel.endsWith(" " + marker)) {
                cleanModel = cleanModel.substring(0, cleanModel.length() - marker.length()).trim();
                if (!cleanSeries.contains(marker)) {
                    cleanSeries = cleanSeries.isBlank() ? marker : marker + " " + cleanSeries;
                }
                break;
            }
        }
        return new ModelSeries(cleanModel, normalizeSeries(cleanSeries));
    }

    private String normalizeSeriesForSource(String series, String source) {
        String normalized = normalizeSeries(series);
        String sourceText = safe(source).toLowerCase();
        boolean mentionsPetrol = sourceText.contains("petrol");
        boolean mentionsDiesel = sourceText.contains("diesel");
        if (!mentionsPetrol && !mentionsDiesel) {
            normalized = normalized
                    .replace("PETROL AND DIESEL", "")
                    .replace("PETROL", "")
                    .replace("DIESEL", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        } else if (!mentionsDiesel) {
            normalized = normalized.replace("PETROL AND DIESEL", "PETROL").replace("DIESEL", "").replaceAll("\\s+", " ").trim();
        } else if (!mentionsPetrol) {
            normalized = normalized.replace("PETROL AND DIESEL", "DIESEL").replace("PETROL", "").replaceAll("\\s+", " ").trim();
        }
        return normalized;
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return text.substring(start, end + 1);
    }

    private String buildPrompt(Part part, String sourceText) {
        return """
            You extract vehicle compatibility for an Indian auto parts inventory system.

            Important:
            In Indian spare parts business, "TYPE 1", "TYPE 2", "TYPE 3", etc. usually means car generation / facelift / old-new model difference.
            Example:
            - Alto Type 1 and Alto Type 2 are different generations.
            - Swift Type 1, Type 2, Type 3, Type 4 are different generations.
            - Dzire Type 1, Type 2, Type 3, Type 4 are different generations.
            - WagonR Type 1 and Type 2 are different generations.
            - If source mentions old model, new model, 1st gen, 2nd gen, old shape, new shape, facelift, or year range, convert that into the closest series/type.
            - Return every compatible model mentioned in the source. Do not collapse Swift and Dzire into one row.

            Rules:
            - Use ONLY the source text.
            - Do not guess compatibility from memory.
            - Do not invent vehicles.
            - The part number, part name, and company name are identifiers, not compatibility evidence.
            - If source text does not clearly mention compatible vehicles, return {"models":[]}.
            - If source text says a vehicle does not fit, is not compatible, or requires a different part number, never return that vehicle.
            - Every returned row must have a source URL copied from the source text.
            - Keep the car model name clean. Do not put Type 1, Type 2, Type 3, Type 4, petrol, diesel, or year range inside "model"; put those details in "series".
            - If company is MGP, Maruti Genuine Parts, or Maruti Suzuki, return only Maruti Suzuki vehicles.
            - If company is Hyundai or Hyundai Genuine Parts, return only Hyundai vehicles.
            - There is no model or generation called STANDARD. Never return STANDARD in model or series.
            - If the source does not mention type, generation, trim, fuel, or year range, return no row for that vehicle.
            - If source says variants/trims like LXI, VXI, ZXI, LDI, VDI, ZDI, include them in series when available.
            - If source says Petrol & Diesel, Petrol/Diesel, or Petrol and Diesel, include both as "PETROL AND DIESEL".
            - If source mentions only petrol or only diesel, include that fuel in series, for example "TYPE 2 DIESEL" or "PETROL".
            - If source gives year range but no type, put year range in series, for example "2012-2017".
            - Prefer uppercase series values like "TYPE 1", "TYPE 2", "TYPE 2 PETROL AND DIESEL", "TYPE 1 LXI VXI ZXI", "DIESEL", "PETROL", "OLD MODEL", "NEW MODEL", or a year range.
            - Every returned source must include marutisuzuki.com or boodmo.com.
            - Return JSON only. No explanation outside JSON.

            Output format:
            {
              "models": [
                {
                  "brand": "Maruti Suzuki",
                  "model": "Swift",
                  "series": "TYPE 2 DIESEL",
                  "confidence": "high",
                  "source": "https://www.marutisuzuki.com/..."
                }
              ]
            }

            Part:
            Company: %s
            Part Number: %s
            Part Name: %s

            Source text:
            %s
            """.formatted(
                safe(part.getCompanyName()),
                safe(part.getPartNumber()),
                safe(part.getName()),
                safe(sourceText)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeSeries(String value) {
        if (value == null || value.trim().isBlank()) return "";

        String text = value.trim().toUpperCase();
        if (text.equals("STANDARD") || text.equals("STD") || text.equals("UNIVERSAL") || text.equals("ALL")) {
            return "";
        }
        text = text.replace("PETROL & DIESEL", "PETROL AND DIESEL")
                .replace("PETROL/DIESEL", "PETROL AND DIESEL")
                .replace("PETROL + DIESEL", "PETROL AND DIESEL");

        text = text.replace("TYPE-1", "TYPE 1")
                .replace("TYPE1", "TYPE 1")
                .replace("T1", "TYPE 1")
                .replace("1ST GEN", "TYPE 1")
                .replace("FIRST GEN", "TYPE 1");

        text = text.replace("TYPE-2", "TYPE 2")
                .replace("TYPE2", "TYPE 2")
                .replace("T2", "TYPE 2")
                .replace("2ND GEN", "TYPE 2")
                .replace("SECOND GEN", "TYPE 2");

        text = text.replace("TYPE-3", "TYPE 3")
                .replace("TYPE3", "TYPE 3")
                .replace("T3", "TYPE 3")
                .replace("3RD GEN", "TYPE 3")
                .replace("THIRD GEN", "TYPE 3");

        text = text.replace("TYPE-4", "TYPE 4")
                .replace("TYPE4", "TYPE 4")
                .replace("T4", "TYPE 4")
                .replace("4TH GEN", "TYPE 4")
                .replace("FOURTH GEN", "TYPE 4");

        text = text.replaceAll("\\s+", " ").trim();

        return text.isBlank() ? "" : text;
    }

    private record ModelSeries(String modelName, String series) {
    }
}
