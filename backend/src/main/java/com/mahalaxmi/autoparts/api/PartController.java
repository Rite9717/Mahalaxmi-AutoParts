package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.repository.BillItemRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.PurchaseItemRepository;
import com.mahalaxmi.autoparts.repository.StockTransactionRepository;
import com.mahalaxmi.autoparts.service.CompatibilityLookupService;
import com.mahalaxmi.autoparts.service.InventoryService;
import com.mahalaxmi.autoparts.service.OllamaService;
import com.mahalaxmi.autoparts.service.OpenAiCompatibilityService;
import jakarta.validation.Valid;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api")
public class PartController {
    private final PartRepository parts;
    private final BillItemRepository billItems;
    private final PurchaseItemRepository purchaseItems;
    private final StockTransactionRepository transactions;
    private final InventoryService inventory;
    private final CompatibilityLookupService compatibilityLookup;
    private final String adminPassword;
    private final OllamaService ollamaService;
    private final OpenAiCompatibilityService openAiCompatibility;

    public PartController(
            PartRepository parts,
            BillItemRepository billItems,
            PurchaseItemRepository purchaseItems,
            StockTransactionRepository transactions,
            InventoryService inventory,
            CompatibilityLookupService compatibilityLookup,
            @Value("${app.admin.password:1234}") String adminPassword,
            OllamaService ollamaService,
            OpenAiCompatibilityService openAiCompatibility
    ) {
        this.parts = parts;
        this.billItems = billItems;
        this.purchaseItems = purchaseItems;
        this.transactions = transactions;
        this.inventory = inventory;
        this.compatibilityLookup = compatibilityLookup;
        this.adminPassword = adminPassword;
        this.ollamaService = ollamaService;
        this.openAiCompatibility = openAiCompatibility;
    }

    @GetMapping("/parts")
    @Transactional(readOnly = true)
    public List<Dtos.PartResponse> listParts(@RequestParam(required = false) String search, @RequestParam(required = false) Long modelId) {
        String normalizedSearch = search == null || search.trim().isEmpty() ? null : search.trim();
        return parts.search(normalizedSearch, modelId).stream().map(ApiMapper::part).toList();
    }

    @GetMapping("/parts/{id}")
    @Transactional(readOnly = true)
    public Dtos.PartResponse part(@PathVariable long id) {
        return ApiMapper.part(inventory.getPart(id));
    }

    @PostMapping("/parts")
    @Transactional
    public Dtos.PartResponse createPart(@Valid @RequestBody Dtos.PartRequest request) {
        return ApiMapper.part(inventory.createPart(request));
    }

    @PutMapping("/parts/{id}")
    @Transactional
    public Dtos.PartResponse updatePart(@PathVariable long id, @Valid @RequestBody Dtos.PartRequest request) {
        return ApiMapper.part(inventory.updatePart(id, request));
    }

    @PostMapping("/parts/{id}/fetch-compatibility")
    public Dtos.CompatibilityFetchResponse fetchCompatibility(@PathVariable long id) {
        return openAiCompatibility.fetchAndSave(id);
    }

    @PostMapping("/parts/fetch-missing-compatibility")
    public List<Dtos.CompatibilityFetchResponse> fetchMissingCompatibility(@RequestParam(defaultValue = "25") int limit) {
        return openAiCompatibility.fetchMissing(limit);
    }

    @PostMapping("/parts/{id}/fetch-compatibility-openai")
    public Dtos.CompatibilityFetchResponse fetchCompatibilityOpenAi(@PathVariable long id) {
        return openAiCompatibility.fetchAndSave(id);
    }

    @PostMapping("/parts/fetch-missing-compatibility-openai")
    public List<Dtos.CompatibilityFetchResponse> fetchMissingCompatibilityOpenAi(@RequestParam(defaultValue = "10") int limit) {
        return openAiCompatibility.fetchMissing(limit);
    }

    @PostMapping("/parts/fetch-missing-mgp-compatibility-openai")
    public List<Dtos.CompatibilityFetchResponse> fetchMissingMgpCompatibilityOpenAi(@RequestParam(defaultValue = "10") int limit) {
        return openAiCompatibility.fetchMissingMgp(limit);
    }

    @GetMapping("/parts/{id}/compatibility-ai-preview")
    public Dtos.CompatibilityAiPreviewResponse compatibilityAiPreviewGet(@PathVariable long id) {
        return compatibilityAiPreview(id);
    }

    @PostMapping("/parts/{id}/compatibility-ai-preview")
    public Dtos.CompatibilityAiPreviewResponse compatibilityAiPreview(@PathVariable long id) {
        Part part = parts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Part not found"));
        String sourceText = compatibilityLookup.collectSourceText(part);
        List<Dtos.CompatibilityAiSuggestion> suggestions;
        String message;
        try {
            suggestions = ollamaService.suggestCompatibility(part, sourceText);
            message = suggestions.isEmpty()
                    ? "No verified compatibility suggestions found."
                    : "Found " + suggestions.size() + " compatibility suggestion(s).";
        } catch (RuntimeException ex) {
            suggestions = List.of();
            message = "Compatibility AI preview failed: " + ex.getMessage();
        }
        return new Dtos.CompatibilityAiPreviewResponse(
                part.getId(),
                part.getName(),
                part.getPartNumber(),
                part.getCompanyName(),
                suggestions,
                message
        );
    }

    @PatchMapping("/parts/{id}/stock")
    @Transactional
    public Dtos.PartResponse updateStock(@PathVariable long id, @Valid @RequestBody Dtos.StockUpdateRequest request) {
        return ApiMapper.part(inventory.updateStock(id, request));
    }

    @DeleteMapping("/parts/{id}")
    @Transactional
    public ResponseEntity<Void> deletePart(@PathVariable long id, @RequestHeader(name = "X-Admin-Password", required = false) String password) {
        if (adminPassword == null || adminPassword.isBlank() || password == null || !adminPassword.equals(password)) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid admin password");
        }
        Part part = parts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Part not found"));
        part.getCompatibleModels().clear();
        if (billItems.existsByPart_Id(id) || purchaseItems.existsByPart_Id(id)) {
            part.setActive(false);
            part.setStockLevel(0);
            part.setPartNumber(null);
            part.setCarCompatibility("Deleted from inventory");
        } else {
            transactions.deleteByPart_Id(id);
            parts.delete(part);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stock-transactions")
    @Transactional(readOnly = true)
    public List<Dtos.StockTransactionResponse> stockTransactions() {
        return transactions.findTop100ByOrderByCreatedAtDesc().stream().map(ApiMapper::transaction).toList();
    }
}
