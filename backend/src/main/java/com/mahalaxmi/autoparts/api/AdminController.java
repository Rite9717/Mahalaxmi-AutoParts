package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.repository.BillItemRepository;
import com.mahalaxmi.autoparts.repository.BillRepository;
import com.mahalaxmi.autoparts.repository.CarBrandRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.PurchaseItemRepository;
import com.mahalaxmi.autoparts.repository.PurchaseRepository;
import com.mahalaxmi.autoparts.repository.StockTransactionRepository;
import com.mahalaxmi.autoparts.repository.SupplierRepository;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final StockTransactionRepository stockTransactions;
    private final BillItemRepository billItems;
    private final BillRepository bills;
    private final PurchaseItemRepository purchaseItems;
    private final PurchaseRepository purchases;
    private final PartRepository parts;
    private final CarModelRepository carModels;
    private final CarBrandRepository carBrands;
    private final SupplierRepository suppliers;

    public AdminController(
            StockTransactionRepository stockTransactions,
            BillItemRepository billItems,
            BillRepository bills,
            PurchaseItemRepository purchaseItems,
            PurchaseRepository purchases,
            PartRepository parts,
            CarModelRepository carModels,
            CarBrandRepository carBrands,
            SupplierRepository suppliers
    ) {
        this.stockTransactions = stockTransactions;
        this.billItems = billItems;
        this.bills = bills;
        this.purchaseItems = purchaseItems;
        this.purchases = purchases;
        this.parts = parts;
        this.carModels = carModels;
        this.carBrands = carBrands;
        this.suppliers = suppliers;
    }

    @DeleteMapping("/clear-data")
    @Transactional
    public Map<String, String> clearData() {
        stockTransactions.deleteAll();
        billItems.deleteAll();
        bills.deleteAll();
        purchaseItems.deleteAll();
        purchases.deleteAll();
        parts.deleteAll();
        carModels.deleteAll();
        carBrands.deleteAll();
        suppliers.deleteAll();
        return Map.of("status", "cleared");
    }
}
