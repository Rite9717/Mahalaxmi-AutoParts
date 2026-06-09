package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.BillItem;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillItemRepository extends JpaRepository<BillItem, Long> {
    boolean existsByPart_Id(Long partId);

    @Query("""
            select bi.partName, bi.partNumber, sum(bi.quantity)
            from BillItem bi
            where bi.bill.status <> 'CANCELLED'
            group by bi.partName, bi.partNumber
            order by sum(bi.quantity) desc
            """)
    List<Object[]> topSelling();

    @Query("select coalesce(sum(bi.grossProfit), 0) from BillItem bi where bi.bill.status <> 'CANCELLED'")
    BigDecimal totalGrossProfit();
}
