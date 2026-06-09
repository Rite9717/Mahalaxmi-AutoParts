package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.CarBrand;
import com.mahalaxmi.autoparts.domain.CarModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarModelRepository extends JpaRepository<CarModel, Long> {
    List<CarModel> findByBrandOrderByNameAsc(CarBrand brand);
    Optional<CarModel> findByBrandAndName(CarBrand brand, String name);
    Optional<CarModel> findByBrandAndNameAndSeries(CarBrand brand, String name, String series);
}
