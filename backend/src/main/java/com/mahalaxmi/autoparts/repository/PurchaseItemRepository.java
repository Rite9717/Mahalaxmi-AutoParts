package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
}
