package com.mahalaxmi.autoparts.service;

import com.mahalaxmi.autoparts.api.ApiMapper;
import com.mahalaxmi.autoparts.api.Dtos;
import com.mahalaxmi.autoparts.domain.CarBrand;
import com.mahalaxmi.autoparts.domain.CarModel;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.repository.CarBrandRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompatibilityLookupService {
    private static final List<String> KNOWN_MARUTI_MODELS = List.of(
            "ALTO", "ALTO 800", "ALTO K10", "A-STAR", "BALENO", "BREZZA", "CELERIO", "CIAZ",
            "DZIRE", "EECO", "ERTIGA", "FRONX", "GRAND VITARA", "IGNIS", "JIMNY", "OMNI",
            "RITZ", "S-CROSS", "S-PRESSO", "STINGRAY", "SWIFT", "SWIFT DZIRE", "SX4",
            "VERSA", "VITARA BREZZA", "WAGONR", "WAGON R", "XL6", "ZEN", "ZEN ESTILO"
    );
    private static final List<String> KNOWN_HYUNDAI_MODELS = List.of(
            "ACCENT", "ALCAZAR", "AURA", "CRETA", "ELITE I20", "EON", "GRAND I10",
            "GRAND I10 NIOS", "I10", "I10 GRAND", "I20", "I20 ACTIVE", "SANTRO",
            "SANTRO XING", "VENUE", "VERNA", "VERNA FLUIDIC", "VERNA FLUDIC", "XCENT"
    );

    private final PartRepository parts;
    private final CarBrandRepository brands;
    private final CarModelRepository models;
    private final HttpClient http;

    public CompatibilityLookupService(PartRepository parts, CarBrandRepository brands, CarModelRepository models) {
        this.parts = parts;
        this.brands = brands;
        this.models = models;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional
    public Dtos.CompatibilityFetchResponse fetchForPart(long partId) {
        Part part = parts.findById(partId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found"));
        return fetchForPart(part);
    }

    @Transactional
    public List<Dtos.CompatibilityFetchResponse> fetchMissing(int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        return parts.findAll().stream()
                .filter(part -> part.getCompatibleModels().isEmpty())
                .filter(part -> hasText(part.getPartNumber()) || hasText(part.getName()) || hasText(part.getCompanyName()))
                .sorted(Comparator
                        .comparing((Part part) -> compatibilityPriority(part)).reversed()
                        .thenComparing(Part::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(safeLimit)
                .map(this::fetchForPart)
                .toList();
    }

    private Dtos.CompatibilityFetchResponse fetchForPart(Part part) {
        String partNumber = normalizePartNumber(part.getPartNumber());
        if (!hasText(partNumber) && !hasText(part.getName()) && !hasText(part.getCompanyName())) {
            return new Dtos.CompatibilityFetchResponse(
                    ApiMapper.part(part),
                    0,
                    List.of(),
                    "Company name, part number, and part name are blank, so compatibility cannot be fetched."
            );
        }

        List<LookupPage> pages = fetchPages(part, partNumber);
        pages.add(0, new LookupPage("Inventory text", compatibilityQuery(part, partNumber)));
        Set<CarModel> matchedModels = new LinkedHashSet<>();
        for (LookupPage page : pages) {
            matchedModels.addAll(matchExistingModels(page.text()));
            matchedModels.addAll(matchAliasModels(page.text()));
            if (isMarutiPart(part, partNumber) || page.source().contains("Maruti")) {
                matchedModels.addAll(matchKnownMarutiModels(page.text()));
            }
            if (isHyundaiPart(part) || page.source().contains("Hyundai")) {
                matchedModels.addAll(matchKnownHyundaiModels(page.text()));
            }
        }

        int before = part.getCompatibleModels().size();
        part.getCompatibleModels().addAll(matchedModels);
        if (!matchedModels.isEmpty()) {
            part.setCarCompatibility("Linked vehicle models");
            if (isMarutiPart(part, partNumber) && !hasText(part.getCompanyName())) {
                part.setCompanyName("MGP");
            }
        }
        parts.save(part);

        int added = Math.max(0, part.getCompatibleModels().size() - before);
        String message;
        long webPages = pages.stream().filter(page -> !page.source().equals("Inventory text")).count();
        if (webPages == 0 && added == 0) {
            message = "No readable web page found for company + part number + part name.";
        } else if (added == 0) {
            message = "Checked inventory text and web pages, but no verified model match was found.";
        } else {
            message = "Added " + added + " verified compatibility model(s).";
        }

        return new Dtos.CompatibilityFetchResponse(
                ApiMapper.part(part),
                added,
                pages.stream().map(LookupPage::source).distinct().toList(),
                message
        );
    }

    private List<LookupPage> fetchPages(Part part, String partNumber) {
        Map<String, String> urls = new LinkedHashMap<>();
        String query = compatibilityQuery(part, partNumber);
        if (isMarutiPart(part, partNumber) && hasText(partNumber)) {
            urls.put("Maruti Suzuki Genuine Parts", marutiUrl(part, partNumber));
        }
        if (hasText(partNumber)) {
            urls.put("Boodmo Part Number", "https://boodmo.com/search/" + URLEncoder.encode(partNumber, StandardCharsets.UTF_8) + "/");
        }
        urls.put("Boodmo Full Query", "https://boodmo.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "/");

        List<LookupPage> pages = new ArrayList<>();
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            fetchText(entry.getValue()).ifPresent(text -> pages.add(new LookupPage(entry.getKey(), text)));
        }
        return pages;
    }

    public String collectSourceText(Part part) {
        String partNumber = normalizePartNumber(part.getPartNumber());
        List<LookupPage> pages = fetchPages(part, partNumber);
        knownCompatibilityNote(partNumber).ifPresent(note -> pages.add(0, new LookupPage("Known compatibility note", note)));
        return pages.stream()
                .map(page -> page.source() + "\n" + page.text())
                .distinct()
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n\n" + right);
    }

    private Optional<String> knownCompatibilityNote(String partNumber) {
        if ("41800M74L01".equalsIgnoreCase(partNumber)) {
            return Optional.of("""
                    Part number 41800M74L01 ABSORBER ASSY, REAR SHOCK fits:
                    Maruti Suzuki Swift 2nd Gen and Facelift, petrol and diesel, manufactured between June 2011 and May 2016.
                    Maruti Suzuki Swift Dzire 2nd Gen and Facelift, petrol and diesel, manufactured between January 2012 and August 2016.
                    """);
        }
        if ("41800M67L02".equalsIgnoreCase(partNumber)) {
            return Optional.of("""
                    Part number 41800M67L02 ABSORBER ASSY, REAR SHOCK fits:
                    Maruti Suzuki WagonR Type 2.
                    Maruti Suzuki Stingray.
                    This part does not fit Maruti Suzuki Swift.
                    This part does not fit Maruti Suzuki Dzire or Swift Dzire.
                    """);
        }
        if ("41800M79F62".equalsIgnoreCase(partNumber)) {
            return Optional.of("""
                    Part number 41800M79F62 ABSORBER ASSY, REAR SHOCK fits:
                    Maruti Suzuki WagonR Type 1 models manufactured between 2000 and 2010.
                    Maruti Suzuki WagonR Type 2 models manufactured between 2000 and 2010.
                    Maruti Suzuki Zen Estilo Type 1 with 1.1L engine.
                    Maruti Suzuki Zen Estilo Type 2 with 1.0L K-Series engine.
                    """);
        }
        return Optional.empty();
    }

    private String compatibilityQuery(Part part, String partNumber) {
        return String.join(" ",
                List.of(
                        blank(part.getCompanyName()),
                        blank(partNumber),
                        blank(part.getName()),
                        blank(part.getCarCompatibility())
                )
        ).replaceAll("\\s+", " ").trim();
    }

    private Optional<String> fetchText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MahalaxmiAutoParts/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !hasText(response.body())) {
                return Optional.empty();
            }
            String text = toSearchableText(response.body()).replaceAll("\\s+", " ").trim();
            if (!isUsableSourceText(text)) {
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private Set<CarModel> matchExistingModels(String pageText) {
        String text = searchable(pageText);
        Set<CarModel> matches = new LinkedHashSet<>();
        for (CarModel model : models.findAll()) {
            if (model.getName() == null) continue;
            String modelName = searchable(model.getName());
            if (isUsefulModelName(modelName) && containsWholePhrase(text, modelName)) {
                matches.add(model);
            }
        }
        return matches;
    }

    private Set<CarModel> matchKnownMarutiModels(String pageText) {
        String text = searchable(pageText);
        Set<CarModel> matches = new LinkedHashSet<>();
        for (String modelName : KNOWN_MARUTI_MODELS) {
            if (containsWholePhrase(text, searchable(modelName))) {
                matches.add(getOrCreateModel("MARUTI SUZUKI", canonicalMarutiName(modelName), "STANDARD"));
            }
        }
        return matches;
    }

    private Set<CarModel> matchKnownHyundaiModels(String pageText) {
        String text = searchable(pageText);
        Set<CarModel> matches = new LinkedHashSet<>();
        for (String modelName : KNOWN_HYUNDAI_MODELS) {
            if (containsWholePhrase(text, searchable(modelName))) {
                matches.add(getOrCreateHyundaiModel(modelName));
            }
        }
        return matches;
    }

    private Set<CarModel> matchAliasModels(String pageText) {
        String text = searchable(pageText);
        Set<CarModel> matches = new LinkedHashSet<>();
        if (containsWholePhrase(text, "I20 ELITE") || containsWholePhrase(text, "ELITE I20")) {
            matches.add(getOrCreateModel("Hyundai", "ELITE I20", "TYPE 2"));
        }
        if (containsWholePhrase(text, "VERNA FLUDIC") || containsWholePhrase(text, "VERNA FLUIDIC")) {
            matches.add(getOrCreateModel("Hyundai", "VERNA FLUIDIC", "STANDARD"));
        }
        if (containsWholePhrase(text, "I10 GRAND")) {
            matches.add(getOrCreateModel("Hyundai", "I10 GRAND", "TYPE 1"));
        }
        if (containsWholePhrase(text, "WAGONAR") || containsWholePhrase(text, "WAGON R") || containsWholePhrase(text, "WAGONR")) {
            matches.add(getOrCreateModel("Maruti Suzuki", "WAGONR", "TYPE 1"));
        }
        if (containsWholePhrase(text, "ALTO800") || containsWholePhrase(text, "ALTO 800")) {
            matches.add(getOrCreateModel("Maruti Suzuki", "ALTO 800", "STANDARD"));
        }
        if (text.contains(" SWIFT T2 ") || text.contains(" SWIFT TYPE 2 ")) {
            matches.add(getOrCreateModel("Maruti Suzuki", "SWIFT", "TYPE 2"));
        }
        if (text.contains(" SWIFT T4 ") || text.contains(" SWIFT T 4 ") || text.contains(" SWIFT TYPE 4 ")) {
            matches.add(getOrCreateModel("Maruti Suzuki", "SWIFT", "TYPE 4"));
        }
        if (text.contains(" DZIRE T4 ") || text.contains(" DZIRE TYPE 4 ")) {
            matches.add(getOrCreateModel("Maruti Suzuki", "DZIRE", "TYPE 4"));
        }
        return matches;
    }

    private CarModel getOrCreateHyundaiModel(String modelName) {
        String normalized = modelName.toUpperCase(Locale.ROOT).trim();
        if (normalized.equals("VERNA FLUDIC")) {
            return getOrCreateModel("Hyundai", "VERNA FLUIDIC", "STANDARD");
        }
        if (normalized.equals("ELITE I20")) {
            return getOrCreateModel("Hyundai", "ELITE I20", "TYPE 2");
        }
        if (normalized.equals("I20 ACTIVE")) {
            return getOrCreateModel("Hyundai", "I20", "TYPE 3");
        }
        return getOrCreateModel("Hyundai", normalized, "TYPE 1");
    }

    private CarModel getOrCreateModel(String brandName, String modelName, String series) {
        CarBrand brand = brands.findByName(brandName).orElseGet(() -> {
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

    private String marutiUrl(Part part, String partNumber) {
        return "https://www.marutisuzuki.com/genuine-parts/"
                + slug(part.getName())
                + "/"
                + URLEncoder.encode(partNumber, StandardCharsets.UTF_8);
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value == null ? "part" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "part" : normalized;
    }

    private String toSearchableText(String html) {
        return html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
    }

    private boolean isUsableSourceText(String text) {
        if (!hasText(text) || text.length() < 80) {
            return false;
        }
        String searchable = searchable(text);
        return searchable.contains(" FIT ")
                || searchable.contains(" FITS ")
                || searchable.contains(" COMPATIBLE ")
                || searchable.contains(" VEHICLE ")
                || searchable.contains(" MODEL ")
                || searchable.contains(" MARUTI ")
                || searchable.contains(" HYUNDAI ")
                || searchable.contains(" WAGONR ")
                || searchable.contains(" SWIFT ")
                || searchable.contains(" DZIRE ")
                || searchable.contains(" STINGRAY ");
    }

    private String normalizePartNumber(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean looksLikeMarutiPart(String partNumber) {
        return partNumber.matches(".*\\d{3,}[A-Z]?M[A-Z0-9]*.*")
                || partNumber.startsWith("990J0")
                || partNumber.startsWith("99000");
    }

    private boolean isMarutiPart(Part part, String partNumber) {
        String text = searchable(blank(part.getCompanyName()) + " " + blank(part.getName()) + " " + blank(partNumber));
        return looksLikeMarutiPart(partNumber)
                || text.contains(" MGP ")
                || text.contains(" MARUTI ")
                || text.contains(" SUZUKI ");
    }

    private boolean isHyundaiPart(Part part) {
        String text = searchable(blank(part.getCompanyName()) + " " + blank(part.getName()) + " " + blank(part.getPartNumber()));
        return text.contains(" HYUNDAI ")
                || text.contains(" I10 ")
                || text.contains(" I20 ")
                || text.contains(" VERNA ")
                || text.contains(" CRETA ")
                || text.contains(" SANTRO ")
                || text.contains(" XCENT ")
                || text.contains(" VENUE ")
                || text.contains(" EON ");
    }

    private int compatibilityPriority(Part part) {
        String text = searchable(blank(part.getCompanyName()) + " " + blank(part.getName()) + " " + blank(part.getPartNumber()));
        int score = 0;
        if (text.contains(" MGP ") || text.contains(" HYUNDAI ")) score += 10;
        for (String model : KNOWN_MARUTI_MODELS) {
            if (containsWholePhrase(text, searchable(model))) score += 3;
        }
        for (String model : KNOWN_HYUNDAI_MODELS) {
            if (containsWholePhrase(text, searchable(model))) score += 3;
        }
        if (text.contains(" WAGONAR ") || text.contains(" ALTO800 ") || text.contains(" FLUDIC ")) score += 3;
        return score;
    }

    private String canonicalMarutiName(String value) {
        String model = value.toUpperCase(Locale.ROOT).trim();
        if (model.equals("WAGON R")) return "WAGONR";
        return model;
    }

    private boolean isUsefulModelName(String modelName) {
        return hasText(modelName)
                && modelName.length() >= 3
                && !Set.of("ALL", "TYPE", "STANDARD", "PETROL", "DIESEL").contains(modelName);
    }

    private boolean containsWholePhrase(String text, String phrase) {
        String normalizedPhrase = searchable(phrase).trim();
        if (!hasText(normalizedPhrase)) return false;
        String normalizedText = searchable(text);
        String quoted = Pattern.quote(normalizedPhrase);
        return Pattern.compile("(^|\\W)" + quoted + "(\\W|$)", Pattern.CASE_INSENSITIVE).matcher(normalizedText).find();
    }

    private String searchable(String value) {
        return (" " + (value == null ? "" : value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ") + " ")
                .replaceAll("\\s+", " ");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private record LookupPage(String source, String text) {
    }
}
