package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.Bill;
import com.mahalaxmi.autoparts.domain.BillItem;
import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.repository.BillRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final BillRepository bills;

    public ReportController(BillRepository bills) {
        this.bills = bills;
    }

    @GetMapping("/profit")
    @Transactional(readOnly = true)
    public Dtos.ProfitReport profit(
            @RequestParam(defaultValue = "DAY") String mode,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String month
    ) {
        DateRange range = range(mode, date, month);
        List<Bill> selectedBills = bills.findByBillingDateBetweenAndBillTypeAndStatusNotOrderByBillingDateDescCreatedAtDesc(
                range.start(),
                range.end(),
                BillType.FINAL,
                BillStatus.CANCELLED
        );
        List<Dtos.SaleLine> sales = saleLines(selectedBills);
        BigDecimal salesTotal = sales.stream().map(Dtos.SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal purchaseCost = sales.stream().map(Dtos.SaleLine::purchaseTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long quantitySold = sales.stream().mapToLong(Dtos.SaleLine::quantity).sum();
        return new Dtos.ProfitReport(
                range.start(),
                range.end(),
                selectedBills.size(),
                quantitySold,
                money(salesTotal),
                money(purchaseCost),
                money(salesTotal.subtract(purchaseCost)),
                sales
        );
    }

    @GetMapping(value = "/sales.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> salesCsv(
            @RequestParam(defaultValue = "DAY") String mode,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String month
    ) {
        var report = profit(mode, date, month);
        String normalizedMode = "MONTH".equalsIgnoreCase(mode) ? "MONTH" : "DAY";
        String period = "MONTH".equals(normalizedMode)
                ? report.startDate().toString().substring(0, 7)
                : report.startDate().toString();
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Bill No,Customer,Mobile,Part Name,Part No,Company,Qty,Selling Price,Discount,GST,Line Total,Purchase Price,Purchase Total,Profit/Loss\n");
        for (Dtos.SaleLine sale : report.sales()) {
            csv.append(csv(sale.billingDate().toString())).append(',')
                    .append(csv(sale.billNumber())).append(',')
                    .append(csv(sale.customerName())).append(',')
                    .append(csv(sale.customerMobile())).append(',')
                    .append(csv(sale.partName())).append(',')
                    .append(csv(sale.partNumber())).append(',')
                    .append(csv(sale.companyName())).append(',')
                    .append(sale.quantity()).append(',')
                    .append(sale.unitSellingPrice()).append(',')
                    .append(sale.discountAmount()).append(',')
                    .append(sale.gstAmount()).append(',')
                    .append(sale.lineTotal()).append(',')
                    .append(sale.unitPurchasePrice()).append(',')
                    .append(sale.purchaseTotal()).append(',')
                    .append(sale.profitLoss()).append('\n');
        }
        csv.append(",,,,,,,TOTAL,,,,")
                .append(report.salesTotal()).append(',')
                .append(',')
                .append(report.purchaseCost()).append(',')
                .append(report.profitLoss()).append('\n');
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("sales-" + period + ".csv")
                        .build()
                        .toString())
                .body(csv.toString());
    }

    private List<Dtos.SaleLine> saleLines(List<Bill> selectedBills) {
        List<Dtos.SaleLine> sales = new ArrayList<>();
        for (Bill bill : selectedBills) {
            for (BillItem item : bill.getItems()) {
                BigDecimal unitCost = money(item.getUnitCost());
                BigDecimal purchaseTotal = money(unitCost.multiply(BigDecimal.valueOf(item.getQuantity())));
                BigDecimal profit = money(item.getLineTotal().subtract(purchaseTotal));
                sales.add(new Dtos.SaleLine(
                        bill.getBillingDate(),
                        bill.getBillNumber(),
                        bill.getCustomerName(),
                        bill.getCustomerMobile(),
                        item.getPartName(),
                        item.getPartNumber(),
                        item.getCompanyName(),
                        item.getQuantity(),
                        money(item.getUnitPrice()),
                        money(item.getDiscountAmount()),
                        money(item.getGstAmount()),
                        money(item.getLineTotal()),
                        unitCost,
                        purchaseTotal,
                        profit
                ));
            }
        }
        return sales;
    }

    private DateRange range(String mode, LocalDate date, String month) {
        if ("MONTH".equalsIgnoreCase(mode)) {
            YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
            return new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        }
        LocalDate selected = date == null ? LocalDate.now() : date;
        return new DateRange(selected, selected);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
