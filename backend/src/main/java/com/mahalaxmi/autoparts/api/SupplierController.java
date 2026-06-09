package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.repository.SupplierRepository;
import com.mahalaxmi.autoparts.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierRepository suppliers;
    private final InventoryService inventory;

    public SupplierController(SupplierRepository suppliers, InventoryService inventory) {
        this.suppliers = suppliers;
        this.inventory = inventory;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Dtos.SupplierResponse> suppliers() {
        return suppliers.findAllByOrderByNameAsc().stream().map(ApiMapper::supplier).toList();
    }

    @PostMapping
    public Dtos.SupplierResponse createSupplier(@Valid @RequestBody Dtos.SupplierRequest request) {
        return ApiMapper.supplier(inventory.createSupplier(request));
    }

    @PutMapping("/{id}")
    public Dtos.SupplierResponse updateSupplier(@PathVariable long id, @Valid @RequestBody Dtos.SupplierRequest request) {
        return ApiMapper.supplier(inventory.updateSupplier(id, request));
    }
}
