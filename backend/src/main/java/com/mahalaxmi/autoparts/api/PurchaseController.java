package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.repository.PurchaseRepository;
import com.mahalaxmi.autoparts.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseRepository purchases;
    private final InventoryService inventory;

    public PurchaseController(PurchaseRepository purchases, InventoryService inventory) {
        this.purchases = purchases;
        this.inventory = inventory;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Dtos.PurchaseResponse> purchases() {
        return purchases.findTop100ByOrderByCreatedAtDesc().stream().map(ApiMapper::purchase).toList();
    }

    @PostMapping
    public Dtos.PurchaseResponse createPurchase(@Valid @RequestBody Dtos.PurchaseRequest request) {
        return ApiMapper.purchase(inventory.createPurchase(request));
    }

    @PutMapping("/{id}")
    @Transactional
    public Dtos.PurchaseResponse updatePurchase(@PathVariable long id, @Valid @RequestBody Dtos.PurchaseUpdateRequest request) {
        return ApiMapper.purchase(inventory.updatePurchase(id, request));
    }
}
