package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.DealerOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerOrderRepository extends JpaRepository<DealerOrder, Long> {
    List<DealerOrder> findTop100ByOrderByCreatedAtDesc();
    Optional<DealerOrder> findByOrderNumber(String orderNumber);
}
