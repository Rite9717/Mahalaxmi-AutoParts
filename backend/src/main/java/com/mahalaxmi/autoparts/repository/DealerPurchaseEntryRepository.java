package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.DealerPurchaseEntry;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DealerPurchaseEntryRepository extends JpaRepository<DealerPurchaseEntry, Long> {
    List<DealerPurchaseEntry> findTop100ByOrderByPurchaseDateDescCreatedAtDesc();

    @Query("select coalesce(sum(p.totalAmount), 0) from DealerPurchaseEntry p")
    BigDecimal totalManualPurchases();
}
