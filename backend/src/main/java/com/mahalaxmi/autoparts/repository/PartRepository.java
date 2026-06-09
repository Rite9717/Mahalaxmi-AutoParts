package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Part;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, Long> {
    Optional<Part> findByPartNumber(String partNumber);

    Optional<Part> findByNameIgnoreCase(String name);

    List<Part> findByNameIgnoreCaseOrderByIdAsc(String name);

    Optional<Part> findByPartNumberAndActiveTrue(String partNumber);

    Optional<Part> findByNameIgnoreCaseAndActiveTrue(String name);

    List<Part> findByNameIgnoreCaseAndActiveTrueOrderByIdAsc(String name);

    @Query("""
            select distinct p from Part p
            left join p.compatibleModels m
            where p.active = true
              and (:modelId is null or m.id = :modelId)
              and (:search is null or lower(p.name) like lower(concat('%', :search, '%'))
                or lower(p.partNumber) like lower(concat('%', :search, '%'))
                or lower(p.serialNo) like lower(concat('%', :search, '%'))
                or lower(p.hsnCode) like lower(concat('%', :search, '%'))
                or lower(p.companyName) like lower(concat('%', :search, '%'))
                or lower(p.carCompatibility) like lower(concat('%', :search, '%'))
                or str(p.sellingPrice) like concat('%', :search, '%')
                or str(p.costPrice) like concat('%', :search, '%'))
            order by p.name asc
            """)
    List<Part> search(@Param("search") String search, @Param("modelId") Long modelId);

    @Query("""
            select distinct p from Part p
            join p.compatibleModels m
            where p.active = true
              and m.id = :modelId
            order by p.name asc
            """)
    List<Part> findCompatibleParts(@Param("modelId") Long modelId);

    long countByStockLevelLessThanAndActiveTrue(int threshold);
}
