package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Mechanic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MechanicRepository extends JpaRepository<Mechanic, Long> {
    List<Mechanic> findAllByOrderByMechanicNameAscGarageNameAsc();
}
