package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Bill;
import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.domain.Mechanic;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findTop100ByOrderByCreatedAtDesc();
    List<Bill> findTop100ByBillTypeOrderByCreatedAtDesc(BillType billType);
    List<Bill> findTop100ByBillTypeAndStatusNotOrderByCreatedAtDesc(BillType billType, BillStatus status);
    List<Bill> findByMechanicAndBillTypeAndStatusNotOrderByCreatedAtDesc(Mechanic mechanic, BillType billType, BillStatus status);
    long countByBillTypeAndStatusNot(BillType billType, BillStatus status);
    long countByBillTypeAndStatus(BillType billType, BillStatus status);
    long countByMechanicAndBillTypeAndStatusNot(Mechanic mechanic, BillType billType, BillStatus status);
    long countByMechanicAndBillType(Mechanic mechanic, BillType billType);
    List<Bill> findByBillingDateBetweenAndStatusNotOrderByBillingDateDescCreatedAtDesc(LocalDate start, LocalDate end, BillStatus status);
    List<Bill> findByBillingDateBetweenAndBillTypeAndStatusNotOrderByBillingDateDescCreatedAtDesc(LocalDate start, LocalDate end, BillType billType, BillStatus status);
    long countByCreatedAtAfter(Instant start);
    long countByCreatedAtAfterAndStatusNot(Instant start, BillStatus status);
    long countByCreatedAtAfterAndBillTypeAndStatusNot(Instant start, BillType billType, BillStatus status);

    @Query("select coalesce(sum(b.grandTotal), 0) from Bill b where b.billType = 'FINAL' and b.status <> 'CANCELLED'")
    java.math.BigDecimal totalRevenue();

    @Query("select coalesce(sum(b.balanceAmount), 0) from Bill b where b.billType = 'ONGOING' and b.status <> 'CANCELLED'")
    java.math.BigDecimal totalReceivable();

    @Query("select coalesce(sum(b.balanceAmount), 0) from Bill b where b.status <> 'CANCELLED'")
    java.math.BigDecimal totalOutstandingBalance();

    @Query("select coalesce(sum(b.balanceAmount), 0) from Bill b where b.mechanic = ?1 and b.billType = 'ONGOING' and b.status <> 'CANCELLED'")
    java.math.BigDecimal receivableFor(Mechanic mechanic);

    @Query("select b from Bill b left join fetch b.items items left join fetch items.part where b.id = :id")
    java.util.Optional<Bill> findByIdWithItemsAndParts(@org.springframework.data.repository.query.Param("id") Long id);
}
