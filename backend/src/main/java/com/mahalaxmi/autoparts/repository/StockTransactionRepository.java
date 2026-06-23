package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.StockTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findTop100ByOrderByCreatedAtDesc();
    long countByPart_Id(Long partId);
    void deleteByPart_Id(Long partId);
    void deleteByBill_Id(Long billId);
}
