package com.mahalaxmi.autoparts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahalaxmi.autoparts.api.ApiMapper;
import com.mahalaxmi.autoparts.api.Dtos;
import com.mahalaxmi.autoparts.domain.CarBrand;
import com.mahalaxmi.autoparts.domain.CarModel;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.repository.CarBrandRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpenAiCompatibilityService {
    private final PartRepository parts;
    private final CarBrandRepository brands;
    private final CarModelRepository models;
    private final CompatibilityLookupService compatibilityLookup;
    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.compatibility-model:gpt-4.1-mini}")
    private String model;

    public OpenAiCompatibilityService(
            PartRepository parts,
            CarBrandRepository brands,
            CarModelRepository models,
            CompatibilityLookupService compatibilityLookup,
            OllamaService ollamaService,
            ObjectMapper objectMapper
    ) {
        this.parts = parts;
        this.brands = brands;
        this.models = models;
        this.compatibilityLookup = compatibilityLookup;
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Transactional
    public Dtos.CompatibilityFetchResponse fetchAndSave(long partId) {
        Part part = parts.findById(partId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found"));
        return fetchAndSave(part);
    }

    @Transactional
    public List<Dtos.CompatibilityFetchResponse> fetchMissing(int limit) {
        int safeLimit = Math.min(50, Math.max(1, limit));
        return parts.findAll().stream()
                .filter(part -> part.getCompatibleModels().isEmpty())
                .filter(part -> hasText(part.getPartNumber()) || hasText(part.getName()) || hasText(part.getCompanyName()))
                .limit(safeLimit)
                .map(this::fetchAndSave)
                .toList();
    }

    @Transactional
    public List<Dtos.CompatibilityFetchResponse> fetchMissingMgp(int limit) {
        int safeLimit = Math.min(50, Math.max(1, limit));
        return parts.findAll().stream()
                .filter(part -> part.getCompatibleModels().isEmpty())
                .filter(this::isMgpPart)
                .filter(part -> hasText(part.getPartNumber()) || hasText(part.getName()))
                .sorted(java.util.Comparator
                        .comparing((Part part) -> safe(part.getName()).toUpperCase(Locale.ROOT))
                        .thenComparing(part -> safe(part.getPartNumber()).toUpperCase(Locale.ROOT)))
                .limit(safeLimit)
                .map(this::fetchAndSave)
                .toList();
    }

    private Dtos.CompatibilityFetchResponse fetchAndSave(Part part) {
        List<Dtos.CompatibilityAiSuggestion> suggestions = suggest(part);
        int before = part.getCompatibleModels().size();
        Set<String> sources = new LinkedHashSet<>();
        for (Dtos.CompatibilityAiSuggestion suggestion : suggestions) {
            if (!isUsefulSuggestion(suggestion, part)) {
                continue;
            }
            CarModel model = getOrCreateModel(
                    normalizeBrand(suggestion.brand(), part),
                    normalizeModelName(suggestion.model()),
                    normalizeSeries(suggestion.series())
            );
            part.getCompatibleModels().add(model);
            sources.add(suggestion.source());
        }
        int added = Math.max(0, part.getCompatibleModels().size() - before);
        if (added > 0) {
            part.setCarCompatibility("Linked by OpenAI research and Ollama naming");
            if (isMarutiPart(part) && !hasText(part.getCompanyName())) {
                part.setCompanyName("MGP");
            }
            parts.save(part);
        }
        return new Dtos.CompatibilityFetchResponse(
                ApiMapper.part(part),
                added,
                sources.stream().toList(),
                added > 0
                        ? "OpenAI researched trusted sites and Ollama added " + added + " cleaned compatibility model(s)."
                        : "OpenAI checked trusted sites and Ollama found no clean compatibility to save."
        );
    }

    public List<Dtos.CompatibilityAiSuggestion> suggest(Part part) {
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OPENAI_API_KEY is not set. Set it and restart the software.");
        }
        try {
            String researchText = fetchTrustedResearchText(part);
            if (!hasText(researchText)) {
                return List.of();
            }
            return ollamaService.suggestCompatibility(part, researchText);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI + Ollama compatibility lookup failed: " + ex.getMessage(), ex);
        }
    }

    private String fetchTrustedResearchText(Part part) {
        try {
            String localSourceText = compatibilityLookup.collectSourceText(part);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("tools", List.of(Map.of("type", "web_search")));
            payload.put("tool_choice", "required");
            payload.put("max_output_tokens", 4000);
            payload.put("input", researchPrompt(part, localSourceText));

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI request failed: " + response.statusCode() + " " + response.body());
            }
            return extractResearchText(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI web research failed: " + ex.getMessage(), ex);
        }
    }

    private String extractResearchText(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        String text = response.path("output_text").asText("");
        if (!hasText(text)) {
            text = collectResponseText(response.path("output"));
        }
        return safe(text);
    }

    private List<Dtos.CompatibilityAiSuggestion> parseSuggestions(String responseBody, Part part) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        String text = response.path("output_text").asText("");
        if (!hasText(text)) {
            text = collectResponseText(response.path("output"));
        }
        String json = extractJson(text);
        if (!hasText(json)) {
            return List.of();
        }
        List<JsonNode> rows = modelRows(json);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Dtos.CompatibilityAiSuggestion> suggestions = new ArrayList<>();
        for (JsonNode row : rows) {
            String brand = normalizeBrand(row.path("brand").asText(""), part);
            String modelName = normalizeModelName(row.path("model").asText(""));
            String series = normalizeSeries(row.path("series").asText(""));
            String confidence = row.path("confidence").asText("");
            String source = row.path("source").asText("");
            if (!hasText(brand) || !hasText(modelName) || !hasText(source)) {
                continue;
            }
            suggestions.add(new Dtos.CompatibilityAiSuggestion(brand, modelName, series, confidence, source.trim()));
        }
        return suggestions;
    }

    private List<JsonNode> modelRows(String json) {
        try {
            JsonNode rows = objectMapper.readTree(json).path("models");
            if (!rows.isArray()) {
                return List.of();
            }
            List<JsonNode> parsed = new ArrayList<>();
            rows.forEach(parsed::add);
            return parsed;
        } catch (Exception ignored) {
            return salvageModelRows(json);
        }
    }

    private List<JsonNode> salvageModelRows(String json) {
        List<JsonNode> rows = new ArrayList<>();
        int modelsIndex = json.indexOf("\"models\"");
        if (modelsIndex < 0) {
            return rows;
        }
        int arrayStart = json.indexOf('[', modelsIndex);
        if (arrayStart < 0) {
            return rows;
        }
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        int objectStart = -1;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escape = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    String objectJson = json.substring(objectStart, i + 1);
                    try {
                        rows.add(objectMapper.readTree(objectJson));
                    } catch (Exception ignored) {
                        // Skip only this malformed row.
                    }
                    objectStart = -1;
                }
            } else if (ch == ']' && depth == 0) {
                break;
            }
        }
        return rows;
    }

    private String collectResponseText(JsonNode output) {
        StringBuilder text = new StringBuilder();
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode block : content) {
                        String value = block.path("text").asText("");
                        if (hasText(value)) {
                            text.append(value).append('\n');
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    private String researchPrompt(Part part, String localSourceText) {
        String company = safe(part.getCompanyName());
        String partNumber = safe(part.getPartNumber());
        String partName = safe(part.getName());
        return """
                Research vehicle compatibility evidence for this Indian auto spare part.

                Search the web using only these trusted sources:
                - site:marutisuzuki.com/genuine-parts
                - site:boodmo.com

                Do not use Amazon, YouTube, random blogs, random marketplace listings, or general car articles.

                Rules:
                - Use company, part number, and part name as the search query.
                - Do not guess from memory.
                - Return only evidence found on marutisuzuki.com/genuine-parts or boodmo.com.
                - Include the source URL beside each evidence line.
                - Include compatible vehicle names exactly as found, including generation, facelift, trim/variant, fuel, and year range when present.
                - Include negative evidence too if a trusted page says the part does not fit a vehicle.
                - Do not format final model names. A local formatter will do that.
                - If no readable trusted page is found, return exactly: NO_TRUSTED_COMPATIBILITY_EVIDENCE.
                - Keep the answer concise but complete.

                Part:
                Company: %s
                Part Number: %s
                Part Name: %s

                Already fetched source text, if any:
                %s
                """.formatted(company, partNumber, partName, safe(localSourceText));
    }

    private boolean isUsefulSuggestion(Dtos.CompatibilityAiSuggestion suggestion, Part part) {
        String confidence = safe(suggestion.confidence()).toLowerCase(Locale.ROOT);
        String source = safe(suggestion.source()).toLowerCase(Locale.ROOT);
        return hasText(suggestion.brand())
                && hasText(suggestion.model())
                && hasText(suggestion.series())
                && hasText(suggestion.source())
                && !safe(suggestion.model()).equalsIgnoreCase("STANDARD")
                && !safe(suggestion.series()).equalsIgnoreCase("STANDARD")
                && (!isMarutiPart(part) || source.contains("marutisuzuki.com") || source.contains("boodmo.com"))
                && !confidence.equals("low");
    }

    private CarModel getOrCreateModel(String brandName, String modelName, String series) {
        CarBrand brand = findBrand(brandName).orElseGet(() -> {
            CarBrand created = new CarBrand();
            created.setName(brandName);
            return brands.save(created);
        });
        return models.findByBrandAndNameAndSeries(brand, modelName, series).orElseGet(() -> {
            CarModel created = new CarModel();
            created.setBrand(brand);
            created.setName(modelName);
            created.setSeries(series);
            return models.save(created);
        });
    }

    private Optional<CarBrand> findBrand(String brandName) {
        return brands.findAll().stream()
                .filter(brand -> brand.getName() != null && brand.getName().equalsIgnoreCase(brandName))
                .findFirst();
    }

    private String normalizeBrand(String value, Part part) {
        String text = safe(value);
        if (isMarutiPart(part) || text.equalsIgnoreCase("MGP") || text.toLowerCase(Locale.ROOT).contains("maruti")) {
            return "Maruti Suzuki";
        }
        if (text.toLowerCase(Locale.ROOT).contains("hyundai")) {
            return "Hyundai";
        }
        return text.isBlank() ? "Maruti Suzuki" : title(text);
    }

    private boolean isMarutiPart(Part part) {
        String text = (safe(part.getCompanyName()) + " " + safe(part.getPartNumber()) + " " + safe(part.getName())).toUpperCase(Locale.ROOT);
        return text.contains("MGP") || text.contains("MARUTI") || text.contains("SUZUKI") || text.matches(".*\\d{3,}[A-Z]?M[A-Z0-9]*.*");
    }

    private boolean isMgpPart(Part part) {
        String company = safe(part.getCompanyName()).toUpperCase(Locale.ROOT);
        return company.equals("MGP")
                || company.equals("MARUTI GENUINE PARTS")
                || company.contains("MARUTI SUZUKI")
                || company.contains("MGP");
    }

    private String normalizeModelName(String value) {
        String text = safe(value).replaceAll("(?i)\\btype\\s*\\d\\b", "").replaceAll("\\s+", " ").trim();
        if (text.equalsIgnoreCase("STANDARD")
                || text.equalsIgnoreCase("STD")
                || text.equalsIgnoreCase("UNIVERSAL")
                || text.equalsIgnoreCase("ALL")
                || text.equalsIgnoreCase("MARUTI SUZUKI")) {
            return "";
        }
        if (text.equalsIgnoreCase("WAGON R") || text.equalsIgnoreCase("WAGONAR")) {
            return "WAGONR";
        }
        if (text.equalsIgnoreCase("SWIFT DZIRE")) {
            return "DZIRE";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private String normalizeSeries(String value) {
        String text = safe(value).toUpperCase(Locale.ROOT);
        if (text.isBlank()) {
            return "";
        }
        if (text.equals("STANDARD") || text.equals("STD") || text.equals("UNIVERSAL") || text.equals("ALL")) {
            return "";
        }
        text = text.replace("1ST GENERATION", "TYPE 1")
                .replace("FIRST GENERATION", "TYPE 1")
                .replace("1ST GEN", "TYPE 1")
                .replace("FIRST GEN", "TYPE 1")
                .replace("2ND GENERATION", "TYPE 2")
                .replace("SECOND GENERATION", "TYPE 2")
                .replace("2ND GEN", "TYPE 2")
                .replace("SECOND GEN", "TYPE 2")
                .replace("3RD GENERATION", "TYPE 3")
                .replace("THIRD GENERATION", "TYPE 3")
                .replace("3RD GEN", "TYPE 3")
                .replace("THIRD GEN", "TYPE 3")
                .replace("4TH GENERATION", "TYPE 4")
                .replace("FOURTH GENERATION", "TYPE 4")
                .replace("4TH GEN", "TYPE 4")
                .replace("FOURTH GEN", "TYPE 4");
        text = text.replace("TYPE-1", "TYPE 1").replace("TYPE1", "TYPE 1")
                .replace("TYPE-2", "TYPE 2").replace("TYPE2", "TYPE 2")
                .replace("TYPE-3", "TYPE 3").replace("TYPE3", "TYPE 3")
                .replace("TYPE-4", "TYPE 4").replace("TYPE4", "TYPE 4")
                .replace("PETROL & DIESEL", "PETROL AND DIESEL")
                .replace("PETROL/DIESEL", "PETROL AND DIESEL")
                .replaceAll("(?i)MANUFACTURED\\s+BETWEEN\\s+(\\d{4})\\s+AND\\s+(\\d{4})", "$1-$2")
                .replaceAll("(?i)MODELS?\\s+MANUFACTURED\\s+BETWEEN\\s+(\\d{4})\\s+AND\\s+(\\d{4})", "$1-$2")
                .replaceAll("(?i)MODELS?\\s+MANUFACTURED", "")
                .replaceAll("(?i)MODELS?", "")
                .replaceAll("(?i)\\b\\d\\.\\dL\\b", "")
                .replaceAll("(?i)\\bK\\s*-?\\s*SERIES\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
        return text.isBlank() ? "" : text;
    }

    private String extractJson(String text) {
        int start = text == null ? -1 : text.indexOf('{');
        int end = text == null ? -1 : text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : "";
    }

    private String title(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String part : lower.split("\\s+")) {
            if (!part.isBlank()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return out.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
