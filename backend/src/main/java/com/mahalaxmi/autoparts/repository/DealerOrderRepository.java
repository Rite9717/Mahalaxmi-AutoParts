package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.DealerOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerOrderRepository extends JpaRepository<DealerOrder, Long> {
    List<DealerOrder> findTop100ByOrderByCreatedAtDesc();
}
