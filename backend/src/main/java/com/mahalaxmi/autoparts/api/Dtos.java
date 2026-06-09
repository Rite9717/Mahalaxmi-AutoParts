package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.BillStatus;
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

    public record BillItemRequest(@NotNull Long partId, @Min(1) int quantity, @DecimalMin("0.0") BigDecimal discountAmount) {
    }

    public record BillRequest(
            String customerName,
            String customerGstin,
            String customerAddress,
            String customerMobile,
            InvoiceType invoiceType,
            LocalDate billingDate,
            SupplyType supplyType,
            String paymentMode,
            String notes,
            @NotEmpty List<BillItemRequest> items
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
            InvoiceType invoiceType,
            LocalDate billingDate,
            SupplyType supplyType,
            String paymentMode,
            BigDecimal subtotal,
            BigDecimal gstTotal,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal grandTotal,
            BillStatus status,
            String notes,
            Instant createdAt,
            List<BillItemResponse> items,
            String printUrl
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
            List<SaleLine> sales
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
}
