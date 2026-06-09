package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.StockTransactionRepository;
import com.mahalaxmi.autoparts.service.InventoryService;
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
    private final StockTransactionRepository transactions;
    private final InventoryService inventory;
    private final String adminPassword;

    public PartController(
            PartRepository parts,
            StockTransactionRepository transactions,
            InventoryService inventory,
            @Value("${app.admin.password:1234}") String adminPassword
    ) {
        this.parts = parts;
        this.transactions = transactions;
        this.inventory = inventory;
        this.adminPassword = adminPassword;
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
        transactions.deleteByPart_Id(id);
        part.getCompatibleModels().clear();
        parts.delete(part);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stock-transactions")
    @Transactional(readOnly = true)
    public List<Dtos.StockTransactionResponse> stockTransactions() {
        return transactions.findTop100ByOrderByCreatedAtDesc().stream().map(ApiMapper::transaction).toList();
    }
}
