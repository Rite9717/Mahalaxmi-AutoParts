package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.DealerPurchaseEntry;
import com.mahalaxmi.autoparts.repository.DealerPurchaseEntryRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/manual-purchases")
public class ManualPurchaseController {
    private final DealerPurchaseEntryRepository purchases;

    public ManualPurchaseController(DealerPurchaseEntryRepository purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Dtos.ManualPurchaseResponse> purchases() {
        return purchases.findTop100ByOrderByPurchaseDateDescCreatedAtDesc().stream()
                .map(ApiMapper::manualPurchase)
                .toList();
    }

    @PostMapping
    @Transactional
    public Dtos.ManualPurchaseResponse create(@Valid @RequestBody Dtos.ManualPurchaseRequest request) {
        if (request.dealerName().trim().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Dealer name is required");
        }
        BigDecimal price = money(request.price());
        DealerPurchaseEntry entry = new DealerPurchaseEntry();
        entry.setDealerName(request.dealerName().trim());
        entry.setQuantity(request.quantity());
        entry.setPrice(price);
        entry.setTotalAmount(money(price.multiply(BigDecimal.valueOf(request.quantity()))));
        entry.setPurchaseDate(request.purchaseDate() == null ? LocalDate.now() : request.purchaseDate());
        return ApiMapper.manualPurchase(purchases.save(entry));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
