package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findAllByOrderByNameAsc();
    Optional<Supplier> findByName(String name);
}
