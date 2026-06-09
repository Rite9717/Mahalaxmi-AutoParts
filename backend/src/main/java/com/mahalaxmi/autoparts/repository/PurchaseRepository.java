package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Purchase;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findTop100ByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(p.grandTotal), 0) from Purchase p")
    BigDecimal totalPurchases();
}
