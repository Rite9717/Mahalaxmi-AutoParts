package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Bill;
import com.mahalaxmi.autoparts.domain.BillStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findTop100ByOrderByCreatedAtDesc();
    List<Bill> findByBillingDateBetweenAndStatusNotOrderByBillingDateDescCreatedAtDesc(LocalDate start, LocalDate end, BillStatus status);
    long countByCreatedAtAfter(Instant start);
    long countByCreatedAtAfterAndStatusNot(Instant start, BillStatus status);

    @Query("select coalesce(sum(b.grandTotal), 0) from Bill b where b.status <> 'CANCELLED'")
    java.math.BigDecimal totalRevenue();
}
