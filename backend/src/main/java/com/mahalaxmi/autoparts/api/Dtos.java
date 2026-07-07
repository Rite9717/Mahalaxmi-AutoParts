package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.domain.InvoiceType;
import com.mahalaxmi.autoparts.domain.SupplyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record BrandResponse(Long id, String name, Instant createdAt) {
    }

    public record ModelResponse(Long id, Long brandId, String brandName, String name, String series, Integer yearFrom, Integer yearTo) {
    }

    public record ModelRequest(
            @NotBlank String brandName,
            @NotBlank String modelName,
            String series,
            Integer yearFrom,
            Integer yearTo
    ) {
    }

    public record PartResponse(
            Long id,
            String imageUrl,
            String name,
            String partNumber,
            String serialNo,
            String hsnCode,
            String companyName,
            String carCompatibility,
            int stockLevel,
            String warehouseLocation,
            String section,
            String rackNumber,
            String shelfBin,
            String supplier,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            BigDecimal purchaseDiscount,
            BigDecimal gstRate,
            Instant createdAt,
            List<ModelResponse> compatibleModels
    ) {
    }

    public record CompatibilityFetchResponse(
            PartResponse part,
            int matchedModels,
            List<String> sources,
            String message
    ) {
    }

    public record CompatibilityAiPreviewResponse(
            Long partId,
            String partName,
            String partNumber,
            String companyName,
            List<CompatibilityAiSuggestion> suggestions,
            String message
    ) {
    }

    public record PartRequest(
            String imageUrl,
            @NotBlank String name,
            String partNumber,
            String serialNo,
            String hsnCode,
            String companyName,
            String carCompatibility,
            @Min(0) int stockLevel,
            String warehouseLocation,
            String section,
            String rackNumber,
            String shelfBin,
            String supplier,
            @DecimalMin("0.0") BigDecimal costPrice,
            @NotNull @DecimalMin("0.0") BigDecimal sellingPrice,
            @DecimalMin("0.0") BigDecimal purchaseDiscount,
            @DecimalMin("0.0") BigDecimal gstRate,
            List<Long> modelIds
    ) {
    }

    public record StockUpdateRequest(@Min(0) int stockLevel, String note) {
    }

    public record SupplierRequest(
            @NotBlank String name,
            String contactPerson,
            String email,
            String phone,
            String address,
            String website,
            @NotNull @DecimalMin("0.0") BigDecimal defaultDiscount
    ) {
    }

    public record SupplierResponse(
            Long id,
            String name,
            String contactPerson,
            String email,
            String phone,
            String address,
            String website,
            BigDecimal defaultDiscount,
            Instant createdAt
    ) {
    }

    public record BillItemRequest(@NotNull Long partId, @Min(1) int quantity, BigDecimal discountAmount) {
    }

    public record BillRequest(
            String customerName,
            String customerGstin,
            String customerAddress,
            String customerMobile,
            String carNumber,
            String aadhaarNumber,
            InvoiceType invoiceType,
            LocalDate billingDate,
            SupplyType supplyType,
            String paymentMode,
            BillType billType,
            Long mechanicId,
            String jobReference,
            @DecimalMin("0.0") BigDecimal amountReceived,
            String notes,
            @NotEmpty List<BillItemRequest> items
    ) {
        public BillRequest(
                String customerName,
                String customerGstin,
                String customerAddress,
                String customerMobile,
                InvoiceType invoiceType,
                LocalDate billingDate,
                SupplyType supplyType,
                String paymentMode,
                String notes,
                List<BillItemRequest> items
        ) {
            this(customerName, customerGstin, customerAddress, customerMobile, null, null, invoiceType, billingDate, supplyType, paymentMode, BillType.FINAL, null, null, null, notes, items);
        }

        public BillRequest(
                String customerName,
                String customerGstin,
                String customerAddress,
                String customerMobile,
                String carNumber,
                String aadhaarNumber,
                InvoiceType invoiceType,
                LocalDate billingDate,
                SupplyType supplyType,
                String paymentMode,
                String notes,
                List<BillItemRequest> items
        ) {
            this(customerName, customerGstin, customerAddress, customerMobile, carNumber, aadhaarNumber, invoiceType, billingDate, supplyType, paymentMode, BillType.FINAL, null, null, null, notes, items);
        }
    }

    public record BillItemsUpdateRequest(InvoiceType invoiceType, SupplyType supplyType, List<BillItemRequest> items) {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            LocalDate paymentDate,
            String notes
    ) {
    }

    public record PaymentResponse(
            Long id,
            Long billId,
            BigDecimal amount,
            LocalDate paymentDate,
            String notes,
            Instant createdAt
    ) {
    }

    public record BillItemResponse(
            Long id,
            Long partId,
            String partName,
            String partNumber,
            String serialNo,
            String hsnCode,
            String companyName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal gstRate,
            BigDecimal discountAmount,
            BigDecimal taxableValue,
            BigDecimal gstAmount,
            BigDecimal lineTotal
    ) {
    }

    public record BillResponse(
            Long id,
            String billNumber,
            String customerName,
            String customerGstin,
            String customerAddress,
            String customerMobile,
            String carNumber,
            String aadhaarNumber,
            InvoiceType invoiceType,
            LocalDate billingDate,
            SupplyType supplyType,
            String paymentMode,
            BillType billType,
            Long mechanicId,
            String mechanicName,
            String garageName,
            String jobReference,
            BigDecimal subtotal,
            BigDecimal gstTotal,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal grandTotal,
            BigDecimal amountPaid,
            BigDecimal balanceAmount,
            BillStatus status,
            String notes,
            Instant createdAt,
            Instant finalizedAt,
            List<BillItemResponse> items,
            List<PaymentResponse> payments,
            String printUrl
    ) {
    }

    public record MechanicRequest(@NotBlank String mechanicName, @NotBlank String garageName) {
    }

    public record MechanicResponse(
            Long id,
            String mechanicName,
            String garageName,
            BigDecimal totalOutstanding,
            long ongoingBills,
            long completedBills,
            Instant createdAt
    ) {
    }

    public record MechanicDetailResponse(
            MechanicResponse mechanic,
            List<BillResponse> ongoingBills,
            List<BillResponse> completedBills
    ) {
    }

    public record StockTransactionResponse(
            Long id,
            Long partId,
            String partName,
            Long billId,
            String transactionType,
            int quantityChange,
            int stockBefore,
            int stockAfter,
            String note,
            Instant createdAt,
            String createdBy
    ) {
    }

    public record InventorySummary(long total, long lowStock, long active) {
    }

    public record TopSellingPart(String name, String partNumber, long sold) {
    }

    public record RecentBill(Long id, String billNumber, String customer, BigDecimal amount, BillStatus status, Instant createdAt) {
    }

    public record DashboardStats(
            InventorySummary inventory,
            BigDecimal inventoryValue,
            BigDecimal totalRevenue,
            BigDecimal totalPurchases,
            BigDecimal grossProfit,
            long todayBills,
            BigDecimal totalReceivable,
            long ongoingBills,
            long paidBills,
            long pendingBills,
            long mechanicsWithPendingPayments,
            List<MechanicResponse> mechanicOutstanding,
            List<PaymentResponse> recentPayments,
            List<TopSellingPart> topSelling,
            List<RecentBill> recentBills,
            List<PartResponse> lowStockItems
    ) {
    }

    public record ProfitReport(
            LocalDate startDate,
            LocalDate endDate,
            long billCount,
            long quantitySold,
            BigDecimal salesTotal,
            BigDecimal purchaseCost,
            BigDecimal profitLoss,
            List<BillSaleLine> sales
    ) {
    }

    public record BillSaleLine(
            LocalDate billingDate,
            String billNumber,
            String customerName,
            String customerMobile,
            int itemCount,
            int quantity,
            BigDecimal salesTotal,
            BigDecimal gstAmount,
            BigDecimal purchaseTotal,
            BigDecimal profitLoss,
            BigDecimal amountPaid,
            BigDecimal balanceAmount,
            String status
    ) {
    }

    public record SaleLine(
            LocalDate billingDate,
            String billNumber,
            String customerName,
            String customerMobile,
            String partName,
            String partNumber,
            String companyName,
            int quantity,
            BigDecimal unitSellingPrice,
            BigDecimal discountAmount,
            BigDecimal gstAmount,
            BigDecimal lineTotal,
            BigDecimal unitPurchasePrice,
            BigDecimal purchaseTotal,
            BigDecimal profitLoss
    ) {
    }

    public record PurchaseItemRequest(
            @NotNull Long partId,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitCost,
            @NotNull @DecimalMin("0.0") BigDecimal discountPercentage
    ) {
    }

    public record PurchaseRequest(
            @NotNull Long supplierId,
            String dealerInvoiceNumber,
            LocalDate purchaseDate,
            String notes,
            @NotEmpty List<PurchaseItemRequest> items
    ) {
    }

    public record PurchaseUpdateRequest(
            @NotBlank String supplierName,
            String dealerInvoiceNumber,
            @NotNull @DecimalMin("0.0") BigDecimal grandTotal
    ) {
    }

    public record PurchaseItemResponse(
            Long id,
            Long partId,
            String partName,
            String partNumber,
            int quantity,
            BigDecimal unitCost,
            BigDecimal discountPercentage,
            BigDecimal lineTotal
    ) {
    }

    public record InventoryImportRow(
            String serialNo,
            String itemName,
            String partNumber,
            String hsnCode,
            String rackNumber,
            @Min(0) int quantity,
            @NotNull @DecimalMin("0.0") BigDecimal sellingPrice,
            @NotNull @DecimalMin("0.0") BigDecimal purchasePrice,
            @NotNull @DecimalMin("0.0") BigDecimal purchaseDiscount,
            @NotNull @DecimalMin("0.0") BigDecimal totalAmount,
            boolean duplicate,
            String sourceLine
    ) {
    }

    public record InventoryImportSaveRequest(
            boolean allowOverride,
            boolean recordPurchase,
            String supplierName,
            String dealerInvoiceNumber,
            LocalDate purchaseDate,
            String notes,
            @NotEmpty List<InventoryImportRow> rows
    ) {
    }

    public record PurchaseResponse(
            Long id,
            Long supplierId,
            String supplierName,
            String dealerInvoiceNumber,
            LocalDate purchaseDate,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal,
            String notes,
            Instant createdAt,
            List<PurchaseItemResponse> items
    ) {
    }

    public record ManualPurchaseRequest(
            @NotBlank String dealerName,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            LocalDate purchaseDate
    ) {
    }

    public record ManualPurchaseResponse(
            Long id,
            String dealerName,
            int quantity,
            BigDecimal price,
            BigDecimal totalAmount,
            LocalDate purchaseDate,
            Instant createdAt
    ) {
    }

    public record DealerOrderItemRequest(
            String itemName,
            String partNumber,
            @Min(1) int quantity,
            String note
    ) {
    }

    public record DealerOrderRequest(
            String dealerName,
            LocalDate orderDate,
            String notes,
            @NotEmpty List<DealerOrderItemRequest> items
    ) {
    }

    public record DealerOrderUpdateRequest(
            String dealerName,
            LocalDate orderDate,
            String notes,
            @NotEmpty List<DealerOrderItemRequest> items
    ) {
    }

    public record DealerOrderItemResponse(
            Long id,
            String itemName,
            String partNumber,
            int quantity,
            String note
    ) {
    }

    public record DealerOrderResponse(
            Long id,
            String orderNumber,
            String dealerName,
            LocalDate orderDate,
            String notes,
            Instant createdAt,
            List<DealerOrderItemResponse> items,
            String printUrl
    ) {
    }
    public record CompatibilityAiSuggestion(
            String brand,
            String model,
            String series,
            String confidence,
            String source
    ) {}
}
