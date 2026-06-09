package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.CarBrand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarBrandRepository extends JpaRepository<CarBrand, Long> {
    List<CarBrand> findAllByOrderByNameAsc();
    Optional<CarBrand> findByName(String name);
}
