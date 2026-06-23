package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.repository.BillItemRepository;
import com.mahalaxmi.autoparts.repository.BillRepository;
import com.mahalaxmi.autoparts.repository.DealerPurchaseEntryRepository;
import com.mahalaxmi.autoparts.repository.MechanicRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.PaymentRepository;
import com.mahalaxmi.autoparts.repository.PurchaseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final PartRepository parts;
    private final BillRepository bills;
    private final BillItemRepository billItems;
    private final MechanicRepository mechanics;
    private final PaymentRepository payments;
    private final PurchaseRepository purchases;
    private final DealerPurchaseEntryRepository manualPurchases;

    public DashboardController(PartRepository parts, BillRepository bills, BillItemRepository billItems, MechanicRepository mechanics, PaymentRepository payments, PurchaseRepository purchases, DealerPurchaseEntryRepository manualPurchases) {
        this.parts = parts;
        this.bills = bills;
        this.billItems = billItems;
        this.mechanics = mechanics;
        this.payments = payments;
        this.purchases = purchases;
        this.manualPurchases = manualPurchases;
    }

    @GetMapping("/dashboard/stats")
    @Transactional(readOnly = true)
    public Dtos.DashboardStats stats() {
        var allParts = parts.findAll().stream().filter(Part::isActive).toList();
        long total = allParts.size();
        long low = allParts.stream().filter(part -> part.getStockLevel() < 5).count();
        long active = total;
        BigDecimal inventoryValue = allParts.stream()
                .map(part -> part.getCostPrice().multiply(BigDecimal.valueOf(part.getStockLevel())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        return new Dtos.DashboardStats(
                new Dtos.InventorySummary(total, low, active),
                inventoryValue,
                bills.totalRevenue(),
                purchases.totalPurchases().add(manualPurchases.totalManualPurchases()),
                billItems.totalGrossProfit(),
                bills.countByCreatedAtAfterAndBillTypeAndStatusNot(startOfDay, BillType.FINAL, BillStatus.CANCELLED),
                bills.totalOutstandingBalance(),
                bills.countByBillTypeAndStatusNot(BillType.ONGOING, BillStatus.CANCELLED),
                bills.countByBillTypeAndStatus(BillType.FINAL, BillStatus.PAID) + bills.countByBillTypeAndStatus(BillType.FINAL, BillStatus.FULLY_PAID),
                bills.countByBillTypeAndStatus(BillType.ONGOING, BillStatus.PENDING) + bills.countByBillTypeAndStatus(BillType.ONGOING, BillStatus.PARTIALLY_PAID) + bills.countByBillTypeAndStatus(BillType.FINAL, BillStatus.PENDING) + bills.countByBillTypeAndStatus(BillType.FINAL, BillStatus.PARTIALLY_PAID),
                mechanics.findAll().stream().filter(mechanic -> bills.receivableFor(mechanic).compareTo(BigDecimal.ZERO) > 0).count(),
                mechanics.findAllByOrderByMechanicNameAscGarageNameAsc().stream()
                        .map(mechanic -> ApiMapper.mechanic(
                                mechanic,
                                bills.receivableFor(mechanic),
                                bills.countByMechanicAndBillTypeAndStatusNot(mechanic, BillType.ONGOING, BillStatus.CANCELLED),
                                bills.countByMechanicAndBillType(mechanic, BillType.FINAL)
                        ))
                        .filter(row -> row.totalOutstanding().compareTo(BigDecimal.ZERO) > 0)
                        .limit(8)
                        .toList(),
                payments.findTop10ByOrderByPaymentDateDescCreatedAtDesc().stream()
                        .map(ApiMapper::payment)
                        .toList(),
                billItems.topSelling().stream()
                        .limit(5)
                        .map(row -> new Dtos.TopSellingPart((String) row[0], (String) row[1], ((Number) row[2]).longValue()))
                        .toList(),
                bills.findTop100ByBillTypeOrderByCreatedAtDesc(BillType.FINAL).stream()
                        .limit(5)
                        .map(bill -> new Dtos.RecentBill(bill.getId(), bill.getBillNumber(), bill.getCustomerName(), bill.getGrandTotal(), bill.getStatus(), bill.getCreatedAt()))
                        .toList(),
                allParts.stream()
                        .filter(part -> part.getStockLevel() < 5)
                        .sorted(java.util.Comparator.comparingInt(Part::getStockLevel))
                        .limit(8)
                        .map(ApiMapper::part)
                        .toList()
        );
    }

    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "ok", "backend", "spring-boot");
    }
}
