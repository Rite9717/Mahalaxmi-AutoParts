package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.Bill;
import com.mahalaxmi.autoparts.domain.BillItem;
import com.mahalaxmi.autoparts.domain.CarBrand;
import com.mahalaxmi.autoparts.domain.CarModel;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.domain.Purchase;
import com.mahalaxmi.autoparts.domain.PurchaseItem;
import com.mahalaxmi.autoparts.domain.StockTransaction;
import com.mahalaxmi.autoparts.domain.Supplier;
import java.util.Comparator;

public final class ApiMapper {
    private ApiMapper() {
    }

    public static Dtos.BrandResponse brand(CarBrand brand) {
        return new Dtos.BrandResponse(brand.getId(), brand.getName(), brand.getCreatedAt());
    }

    public static Dtos.ModelResponse model(CarModel model) {
        return new Dtos.ModelResponse(
                model.getId(),
                model.getBrand().getId(),
                model.getBrand().getName(),
                model.getName(),
                model.getSeries(),
                model.getYearFrom(),
                model.getYearTo()
        );
    }

    public static Dtos.PartResponse part(Part part) {
        return new Dtos.PartResponse(
                part.getId(),
                part.getImageUrl(),
                part.getName(),
                part.getPartNumber(),
                part.getSerialNo(),
                part.getHsnCode(),
                part.getCompanyName(),
                part.getCarCompatibility(),
                part.getStockLevel(),
                part.getWarehouseLocation(),
                part.getSection(),
                part.getRackNumber(),
                part.getShelfBin(),
                part.getSupplier(),
                part.getCostPrice(),
                part.getSellingPrice(),
                part.getPurchaseDiscount(),
                part.getGstRate(),
                part.getCreatedAt(),
                part.getCompatibleModels().stream()
                        .sorted(Comparator.comparing(CarModel::getName))
                        .map(ApiMapper::model)
                        .toList()
        );
    }

    public static Dtos.SupplierResponse supplier(Supplier supplier) {
        return new Dtos.SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getWebsite(),
                supplier.getDefaultDiscount(),
                supplier.getCreatedAt()
        );
    }

    public static Dtos.BillResponse bill(Bill bill) {
        return new Dtos.BillResponse(
                bill.getId(),
                bill.getBillNumber(),
                bill.getCustomerName(),
                bill.getCustomerGstin(),
                bill.getCustomerAddress(),
                bill.getCustomerMobile(),
                bill.getInvoiceType(),
                bill.getBillingDate(),
                bill.getSupplyType(),
                bill.getPaymentMode(),
                bill.getSubtotal(),
                bill.getGstTotal(),
                bill.getCgst(),
                bill.getSgst(),
                bill.getIgst(),
                bill.getGrandTotal(),
                bill.getStatus(),
                bill.getNotes(),
                bill.getCreatedAt(),
                bill.getItems().stream().map(ApiMapper::billItem).toList(),
                "/api/bills/" + bill.getId() + "/print"
        );
    }

    public static Dtos.BillItemResponse billItem(BillItem item) {
        return new Dtos.BillItemResponse(
                item.getId(),
                item.getPart().getId(),
                item.getPartName(),
                item.getPartNumber(),
                item.getSerialNo(),
                item.getHsnCode(),
                item.getCompanyName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getGstRate(),
                item.getDiscountAmount(),
                item.getTaxableValue(),
                item.getGstAmount(),
                item.getLineTotal()
        );
    }

    public static Dtos.PurchaseResponse purchase(Purchase purchase) {
        return new Dtos.PurchaseResponse(
                purchase.getId(),
                purchase.getSupplier().getId(),
                purchase.getSupplier().getName(),
                purchase.getDealerInvoiceNumber(),
                purchase.getPurchaseDate(),
                purchase.getSubtotal(),
                purchase.getDiscountTotal(),
                purchase.getGrandTotal(),
                purchase.getNotes(),
                purchase.getCreatedAt(),
                purchase.getItems().stream().map(ApiMapper::purchaseItem).toList()
        );
    }

    public static Dtos.PurchaseItemResponse purchaseItem(PurchaseItem item) {
        return new Dtos.PurchaseItemResponse(
                item.getId(),
                item.getPart().getId(),
                item.getPartName(),
                item.getPartNumber(),
                item.getQuantity(),
                item.getUnitCost(),
                item.getDiscountPercentage(),
                item.getLineTotal()
        );
    }

    public static Dtos.StockTransactionResponse transaction(StockTransaction tx) {
        return new Dtos.StockTransactionResponse(
                tx.getId(),
                tx.getPart().getId(),
                tx.getPart().getName(),
                tx.getBill() == null ? null : tx.getBill().getId(),
                tx.getTransactionType(),
                tx.getQuantityChange(),
                tx.getStockBefore(),
                tx.getStockAfter(),
                tx.getNote(),
                tx.getCreatedAt(),
                tx.getCreatedBy()
        );
    }
}
