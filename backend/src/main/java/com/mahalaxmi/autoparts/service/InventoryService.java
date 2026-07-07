package com.mahalaxmi.autoparts.service;

import com.mahalaxmi.autoparts.api.Dtos;
import com.mahalaxmi.autoparts.domain.Bill;
import com.mahalaxmi.autoparts.domain.BillItem;
import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.domain.CarModel;
import com.mahalaxmi.autoparts.domain.InvoiceType;
import com.mahalaxmi.autoparts.domain.Mechanic;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.domain.Payment;
import com.mahalaxmi.autoparts.domain.Purchase;
import com.mahalaxmi.autoparts.domain.PurchaseItem;
import com.mahalaxmi.autoparts.domain.StockTransaction;
import com.mahalaxmi.autoparts.domain.Supplier;
import com.mahalaxmi.autoparts.domain.SupplyType;
import com.mahalaxmi.autoparts.repository.BillRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import com.mahalaxmi.autoparts.repository.MechanicRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.PaymentRepository;
import com.mahalaxmi.autoparts.repository.PurchaseRepository;
import com.mahalaxmi.autoparts.repository.StockTransactionRepository;
import com.mahalaxmi.autoparts.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class InventoryService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final java.util.regex.Pattern GSTIN =
            java.util.regex.Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final PartRepository parts;
    private final CarModelRepository models;
    private final SupplierRepository suppliers;
    private final BillRepository bills;
    private final MechanicRepository mechanics;
    private final PaymentRepository payments;
    private final PurchaseRepository purchases;
    private final StockTransactionRepository stockTransactions;

    public InventoryService(
            PartRepository parts,
            CarModelRepository models,
            SupplierRepository suppliers,
            BillRepository bills,
            MechanicRepository mechanics,
            PaymentRepository payments,
            PurchaseRepository purchases,
            StockTransactionRepository stockTransactions
    ) {
        this.parts = parts;
        this.models = models;
        this.suppliers = suppliers;
        this.bills = bills;
        this.mechanics = mechanics;
        this.payments = payments;
        this.purchases = purchases;
        this.stockTransactions = stockTransactions;
    }

    @Transactional
    public Part createPart(Dtos.PartRequest request) {
        String partNumber = trimToNull(request.partNumber());
        if (partNumber != null) {
            var existing = parts.findByPartNumberAndActiveTrue(partNumber);
            if (existing.isPresent()) {
                throw new ResponseStatusException(BAD_REQUEST, "Item already present in inventory");
            }
        } else {
            parts.findByNameIgnoreCaseAndActiveTrue(request.name().trim()).ifPresent(existing -> {
                throw new ResponseStatusException(BAD_REQUEST, "Item already present in inventory");
            });
        }
        Part part = new Part();
        applyPartRequest(part, request);
        part.setActive(true);
        Part saved = parts.save(part);
        if (saved.getStockLevel() > 0) {
            addStockTransaction(saved, null, "INITIAL_STOCK", saved.getStockLevel(), 0, saved.getStockLevel(), "Initial stock entered with new part");
        }
        return saved;
    }

    @Transactional
    public Part updatePart(long id, Dtos.PartRequest request) {
        Part part = getPart(id);
        String partNumber = trimToNull(request.partNumber());
        if (partNumber != null) {
            parts.findByPartNumberAndActiveTrue(partNumber).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ResponseStatusException(BAD_REQUEST, "Item already present in inventory");
                }
            });
        }
        applyPartRequest(part, request);
        return part;
    }

    @Transactional
    public Part updateStock(long id, Dtos.StockUpdateRequest request) {
        Part part = getPart(id);
        int before = part.getStockLevel();
        part.setStockLevel(request.stockLevel());
        addStockTransaction(
                part,
                null,
                "MANUAL_ADJUSTMENT",
                request.stockLevel() - before,
                before,
                request.stockLevel(),
                blankOrDefault(request.note(), "Manual stock update")
        );
        return part;
    }

    @Transactional
    public Supplier createSupplier(Dtos.SupplierRequest request) {
        suppliers.findByName(request.name()).ifPresent(existing -> {
            throw new ResponseStatusException(BAD_REQUEST, "Supplier already exists");
        });
        Supplier supplier = new Supplier();
        applySupplierRequest(supplier, request);
        return suppliers.save(supplier);
    }

    @Transactional
    public Supplier updateSupplier(long id, Dtos.SupplierRequest request) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Supplier not found"));
        applySupplierRequest(supplier, request);
        return supplier;
    }

    @Transactional
    public Bill createBill(Dtos.BillRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Bill must contain at least one item");
        }

        Map<Long, Integer> requestedByPart = new LinkedHashMap<>();
        Map<Long, BigDecimal> discountAmountByPart = new LinkedHashMap<>();
        for (Dtos.BillItemRequest item : request.items()) {
            requestedByPart.merge(item.partId(), item.quantity(), Integer::sum);
            discountAmountByPart.merge(item.partId(), sanitizeMoney(item.discountAmount()), BigDecimal::add);
        }

        Map<Long, Part> partById = new LinkedHashMap<>();
        parts.findAllById(requestedByPart.keySet()).forEach(part -> partById.put(part.getId(), part));
        for (Long partId : requestedByPart.keySet()) {
            Part part = partById.get(partId);
            if (part == null) {
                throw new ResponseStatusException(NOT_FOUND, "Part not found: " + partId);
            }
            int requestedQuantity = requestedByPart.get(partId);
            if (requestedQuantity <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Quantity must be greater than zero");
            }
            if (part.getSellingPrice() == null || part.getSellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Selling price must be greater than zero for " + part.getName());
            }
            if (part.getStockLevel() < requestedQuantity) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Insufficient stock for " + part.getName() + ". Available: " + part.getStockLevel() + ", requested: " + requestedQuantity
                );
            }
        }

        Bill bill = new Bill();
        bill.setBillNumber(nextBillNumber());
        BillType billType = request.billType() == BillType.ONGOING ? BillType.ONGOING : BillType.FINAL;
        bill.setBillType(billType);
        if (billType == BillType.ONGOING) {
            if (request.mechanicId() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Select a mechanic for an ongoing bill");
            }
            Mechanic mechanic = mechanics.findById(request.mechanicId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mechanic not found"));
            bill.setMechanic(mechanic);
            bill.setJobReference(trimToNull(request.jobReference()));
        } else {
            bill.setMechanic(null);
            bill.setJobReference(null);
        }
        bill.setCustomerName(blankOrDefault(request.customerName(), "Walk-in Customer"));
        InvoiceType invoiceType = request.invoiceType() == null ? InvoiceType.GST : request.invoiceType();
        bill.setInvoiceType(invoiceType);
        bill.setCustomerMobile(trimToNull(request.customerMobile()));
        bill.setCarNumber(trimToNull(request.carNumber()));
        bill.setAadhaarNumber(trimToNull(request.aadhaarNumber()));
        if (invoiceType == InvoiceType.GST) {
            String gstin = trimToNull(request.customerGstin());
            if (gstin != null && !GSTIN.matcher(gstin.toUpperCase()).matches()) {
                throw new ResponseStatusException(BAD_REQUEST, "Customer GST number must be a valid 15-character GSTIN");
            }
            bill.setCustomerGstin(gstin == null ? null : gstin.toUpperCase());
        } else {
            bill.setCustomerGstin(null);
        }
        bill.setCustomerAddress(trimToNull(request.customerAddress()));
        bill.setBillingDate(request.billingDate() == null ? LocalDate.now() : request.billingDate());
        bill.setSupplyType(request.supplyType() == null ? SupplyType.INTRA_STATE : request.supplyType());
        bill.setPaymentMode(blankOrDefault(request.paymentMode(), "CASH"));
        bill.setNotes(trimToNull(request.notes()));

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : requestedByPart.entrySet()) {
            Part part = partById.get(entry.getKey());
            int quantity = entry.getValue();
            BigDecimal discountAmount = discountAmountByPart.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BillItem line = buildLine(part, quantity, discountAmount, invoiceType == InvoiceType.GST);
            bill.addItem(line);
            subtotal = subtotal.add(line.getTaxableValue());
            gstTotal = gstTotal.add(line.getGstAmount());
        }

        subtotal = money(subtotal);
        gstTotal = money(gstTotal);
        bill.setSubtotal(subtotal);
        bill.setGstTotal(gstTotal);
        if (invoiceType == InvoiceType.NORMAL) {
            bill.setIgst(BigDecimal.ZERO);
            bill.setCgst(BigDecimal.ZERO);
            bill.setSgst(BigDecimal.ZERO);
        } else if (bill.getSupplyType() == SupplyType.INTER_STATE) {
            bill.setIgst(gstTotal);
            bill.setCgst(BigDecimal.ZERO);
            bill.setSgst(BigDecimal.ZERO);
        } else {
            BigDecimal half = money(gstTotal.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            bill.setCgst(half);
            bill.setSgst(money(gstTotal.subtract(half)));
            bill.setIgst(BigDecimal.ZERO);
        }
        bill.setGrandTotal(money(subtotal.add(gstTotal)));
        if (billType == BillType.ONGOING) {
            bill.setAmountPaid(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setBalanceAmount(bill.getGrandTotal());
            bill.setStatus(BillStatus.PENDING);
        } else {
            BigDecimal amountReceived = request.amountReceived() == null ? bill.getGrandTotal() : money(request.amountReceived());
            if (amountReceived.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Amount received cannot be negative");
            }
            if (amountReceived.compareTo(bill.getGrandTotal()) > 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Amount received cannot be more than bill total");
            }
            bill.setAmountPaid(amountReceived);
            updateBillBalanceStatus(bill);
            bill.setFinalizedAt(Instant.now());
        }

        Bill saved = bills.save(bill);
        for (BillItem item : saved.getItems()) {
            Part part = item.getPart();
            int before = part.getStockLevel();
            part.setStockLevel(before - item.getQuantity());
            addStockTransaction(
                    part,
                    saved,
                    "BILL_CREATED",
                    -item.getQuantity(),
                    before,
                    part.getStockLevel(),
                    "Stock deducted for " + saved.getBillNumber()
            );
        }

        return saved;
    }

    @Transactional
    public Bill cancelBill(long id) {
        Bill bill = bills.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Bill is already cancelled");
        }
        for (BillItem item : bill.getItems()) {
            Part part = item.getPart();
            int before = part.getStockLevel();
            part.setStockLevel(before + item.getQuantity());
            addStockTransaction(
                    part,
                    bill,
                    "BILL_CANCELLED",
                    item.getQuantity(),
                    before,
                    part.getStockLevel(),
                    "Stock restored after cancelling " + bill.getBillNumber()
            );
        }
        bill.setStatus(BillStatus.CANCELLED);
        return bill;
    }

    @Transactional
    public void deleteCancelledBill(long id) {
        Bill bill = bills.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
        if (bill.getStatus() != BillStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only cancelled bills can be deleted");
        }
        stockTransactions.deleteByBill_Id(bill.getId());
        bills.delete(bill);
    }

    @Transactional
    public Bill updateOngoingBillItems(long id, Dtos.BillItemsUpdateRequest request) {
        Bill bill = bills.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
        ensureEditableOngoingBill(bill);
        if (request.invoiceType() != null) {
            bill.setInvoiceType(request.invoiceType());
            if (request.invoiceType() == InvoiceType.NORMAL) {
                bill.setCustomerGstin(null);
            }
        }
        if (request.supplyType() != null) {
            bill.setSupplyType(request.supplyType());
        }
        for (BillItem item : bill.getItems()) {
            Part part = item.getPart();
            int before = part.getStockLevel();
            part.setStockLevel(before + item.getQuantity());
            addStockTransaction(
                    part,
                    bill,
                    "ONGOING_BILL_EDIT_RESTORE",
                    item.getQuantity(),
                    before,
                    part.getStockLevel(),
                    "Stock restored before editing " + bill.getBillNumber()
            );
        }
        bill.getItems().clear();

        Map<Long, Integer> requestedByPart = new LinkedHashMap<>();
        Map<Long, BigDecimal> discountAmountByPart = new LinkedHashMap<>();
        List<Dtos.BillItemRequest> requestedItems = request.items() == null ? List.of() : request.items();
        for (Dtos.BillItemRequest item : requestedItems) {
            requestedByPart.merge(item.partId(), item.quantity(), Integer::sum);
            discountAmountByPart.merge(item.partId(), sanitizeMoney(item.discountAmount()), BigDecimal::add);
        }

        Map<Long, Part> partById = new LinkedHashMap<>();
        parts.findAllById(requestedByPart.keySet()).forEach(part -> partById.put(part.getId(), part));
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : requestedByPart.entrySet()) {
            Part part = partById.get(entry.getKey());
            if (part == null) {
                throw new ResponseStatusException(NOT_FOUND, "Part not found: " + entry.getKey());
            }
            int quantity = entry.getValue();
            validateBillPart(part, quantity);
            BigDecimal discountAmount = discountAmountByPart.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BillItem line = buildLine(part, quantity, discountAmount, bill.getInvoiceType() == InvoiceType.GST);
            bill.addItem(line);
            subtotal = subtotal.add(line.getTaxableValue());
            gstTotal = gstTotal.add(line.getGstAmount());
        }

        applyTotals(bill, subtotal, gstTotal);
        if (bill.getGrandTotal().compareTo(bill.getAmountPaid()) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Bill total cannot be less than amount already received");
        }
        updateBillBalanceStatus(bill);

        for (BillItem item : bill.getItems()) {
            Part part = item.getPart();
            int before = part.getStockLevel();
            part.setStockLevel(before - item.getQuantity());
            addStockTransaction(
                    part,
                    bill,
                    "ONGOING_BILL_UPDATED",
                    -item.getQuantity(),
                    before,
                    part.getStockLevel(),
                    "Stock reserved for edited " + bill.getBillNumber()
            );
        }
        return bill;
    }

    @Transactional
    public Bill recordPayment(long billId, Dtos.PaymentRequest request) {
        Bill bill = bills.findById(billId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot record payment on a cancelled bill");
        }
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Payment amount must be greater than zero");
        }
        if (amount.compareTo(bill.getBalanceAmount()) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Payment cannot be more than remaining balance");
        }
        Payment payment = new Payment();
        payment.setAmount(amount);
        payment.setPaymentDate(request.paymentDate() == null ? LocalDate.now() : request.paymentDate());
        payment.setNotes(trimToNull(request.notes()));
        bill.addPayment(payment);
        payments.save(payment);
        bill.setAmountPaid(money(bill.getAmountPaid().add(amount)));
        updateBillBalanceStatus(bill);
        return bill;
    }

    @Transactional
    public Bill finalizeOngoingBill(long id) {
        Bill bill = bills.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
        if (bill.getBillType() != BillType.ONGOING) {
            throw new ResponseStatusException(BAD_REQUEST, "Only ongoing bills can be finalized");
        }
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Cancelled bill cannot be finalized");
        }
        if (bill.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Collect full payment before finalizing this credit bill");
        }
        bill.setBillType(BillType.FINAL);
        bill.setStatus(BillStatus.FULLY_PAID);
        bill.setFinalizedAt(Instant.now());
        return bill;
    }

    @Transactional
    public Purchase createPurchase(Dtos.PurchaseRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Purchase must contain at least one item");
        }
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Supplier not found"));

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setDealerInvoiceNumber(trimToNull(request.dealerInvoiceNumber()));
        purchase.setPurchaseDate(request.purchaseDate() == null ? LocalDate.now() : request.purchaseDate());
        purchase.setNotes(trimToNull(request.notes()));

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        for (Dtos.PurchaseItemRequest item : request.items()) {
            Part part = getPart(item.partId());
            int quantity = item.quantity();
            BigDecimal unitCost = money(item.unitCost());
            BigDecimal discountPercentage = sanitizeMoney(item.discountPercentage());
            BigDecimal sellingAmount = money(part.getSellingPrice().multiply(BigDecimal.valueOf(quantity)));

            PurchaseItem purchaseItem = new PurchaseItem();
            purchaseItem.setPart(part);
            purchaseItem.setPartName(part.getName());
            purchaseItem.setPartNumber(blank(part.getPartNumber()));
            purchaseItem.setQuantity(quantity);
            purchaseItem.setUnitCost(unitCost);
            purchaseItem.setDiscountPercentage(discountPercentage);
            purchaseItem.setLineTotal(sellingAmount);
            purchase.addItem(purchaseItem);

            subtotal = subtotal.add(sellingAmount);
        }
        purchase.setSubtotal(money(subtotal));
        purchase.setDiscountTotal(money(discountTotal));
        purchase.setGrandTotal(money(subtotal));

        Purchase saved = purchases.save(purchase);
        for (PurchaseItem item : saved.getItems()) {
            Part part = item.getPart();
            int before = part.getStockLevel();
            part.setStockLevel(before + item.getQuantity());
            part.setCostPrice(item.getUnitCost());
            if (part.getSupplier() == null || part.getSupplier().isBlank()) {
                part.setSupplier(supplier.getName());
            }
            addStockTransaction(
                    part,
                    null,
                    "DEALER_PURCHASE",
                    item.getQuantity(),
                    before,
                    part.getStockLevel(),
                    "Stock purchased from " + supplier.getName()
            );
        }
        return saved;
    }

    @Transactional
    public Purchase updatePurchase(long id, Dtos.PurchaseUpdateRequest request) {
        Purchase purchase = purchases.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Purchase not found"));
        Supplier supplier = suppliers.findByName(request.supplierName().trim())
                .orElseGet(() -> {
                    Supplier created = new Supplier();
                    created.setName(request.supplierName().trim());
                    created.setDefaultDiscount(BigDecimal.ZERO);
                    return suppliers.save(created);
                });
        BigDecimal amount = money(request.grandTotal());
        purchase.setSupplier(supplier);
        purchase.setDealerInvoiceNumber(trimToNull(request.dealerInvoiceNumber()));
        purchase.setSubtotal(amount);
        purchase.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        purchase.setGrandTotal(amount);
        return purchase;
    }

    public Part getPart(long id) {
        return parts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Part not found"));
    }

    private void applyPartRequest(Part part, Dtos.PartRequest request) {
        part.setImageUrl(trimToNull(request.imageUrl()));
        part.setName(request.name().trim());
        part.setPartNumber(trimToNull(request.partNumber()));
        part.setSerialNo(trimToNull(request.serialNo()));
        part.setHsnCode(trimToNull(request.hsnCode()));
        part.setCompanyName(trimToNull(request.companyName()));
        part.setCarCompatibility(blankOrDefault(request.carCompatibility(), "Universal"));
        part.setStockLevel(request.stockLevel());
        part.setWarehouseLocation(blankOrDefault(request.warehouseLocation(), "Main Warehouse"));
        part.setSection(trimToNull(request.section()));
        part.setRackNumber(trimToNull(request.rackNumber()));
        part.setShelfBin(trimToNull(request.shelfBin()));
        part.setSupplier(trimToNull(request.supplier()));
        part.setCostPrice(money(request.costPrice()));
        part.setSellingPrice(money(request.sellingPrice()));
        part.setPurchaseDiscount(sanitizeMoney(request.purchaseDiscount()));
        part.setGstRate(sanitizeMoney(request.gstRate()));

        part.getCompatibleModels().clear();
        if (request.modelIds() != null) {
            for (Long modelId : request.modelIds()) {
                CarModel model = models.findById(modelId)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown car model ID: " + modelId));
                part.getCompatibleModels().add(model);
            }
        }
    }

    private void applySupplierRequest(Supplier supplier, Dtos.SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setContactPerson(trimToNull(request.contactPerson()));
        supplier.setEmail(trimToNull(request.email()));
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setWebsite(trimToNull(request.website()));
        supplier.setDefaultDiscount(sanitizeMoney(request.defaultDiscount()));
    }

    private BillItem buildLine(Part part, int quantity, BigDecimal requestedDiscountAmount, boolean includeGst) {
        BigDecimal gross = part.getSellingPrice().multiply(BigDecimal.valueOf(quantity));
        BigDecimal discount = sanitizeMoney(requestedDiscountAmount);
        BigDecimal gstRate = gstRateOrDefault(part.getGstRate());
        BigDecimal unitCost = nullToZero(part.getCostPrice());
        if (discount.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(gross) > 0) {
            discount = money(gross);
        }
        BigDecimal finalAmount = money(gross.subtract(discount));
        BigDecimal taxable = finalAmount;
        BigDecimal gst = BigDecimal.ZERO;
        if (includeGst) {
            BigDecimal divisor = ONE_HUNDRED.add(gstRate);
            taxable = money(finalAmount.multiply(ONE_HUNDRED).divide(divisor, 4, RoundingMode.HALF_UP));
            gst = money(finalAmount.subtract(taxable));
        }

        BillItem item = new BillItem();
        item.setPart(part);
        item.setPartName(part.getName());
        item.setPartNumber(blank(part.getPartNumber()));
        item.setSerialNo(trimToNull(part.getSerialNo()));
        item.setHsnCode(trimToNull(part.getHsnCode()));
        item.setCompanyName(trimToNull(part.getCompanyName()));
        item.setQuantity(quantity);
        item.setUnitPrice(money(finalAmount.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP)));
        item.setUnitCost(unitCost);
        item.setGstRate(gstRate);
        item.setDiscountPercentage(BigDecimal.ZERO);
        item.setDiscountAmount(discount);
        item.setTaxableValue(taxable);
        item.setGstAmount(gst);
        item.setLineTotal(finalAmount);
        item.setGrossProfit(money(taxable.subtract(unitCost.multiply(BigDecimal.valueOf(quantity)))));
        return item;
    }

    private void validateBillPart(Part part, int requestedQuantity) {
        if (requestedQuantity <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Quantity must be greater than zero");
        }
        if (part.getSellingPrice() == null || part.getSellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Selling price must be greater than zero for " + part.getName());
        }
        if (part.getStockLevel() < requestedQuantity) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Insufficient stock for " + part.getName() + ". Available: " + part.getStockLevel() + ", requested: " + requestedQuantity
            );
        }
    }

    private void applyTotals(Bill bill, BigDecimal subtotal, BigDecimal gstTotal) {
        subtotal = money(subtotal);
        gstTotal = money(gstTotal);
        bill.setSubtotal(subtotal);
        bill.setGstTotal(gstTotal);
        if (bill.getInvoiceType() == InvoiceType.NORMAL) {
            bill.setIgst(BigDecimal.ZERO);
            bill.setCgst(BigDecimal.ZERO);
            bill.setSgst(BigDecimal.ZERO);
        } else if (bill.getSupplyType() == SupplyType.INTER_STATE) {
            bill.setIgst(gstTotal);
            bill.setCgst(BigDecimal.ZERO);
            bill.setSgst(BigDecimal.ZERO);
        } else {
            BigDecimal half = money(gstTotal.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            bill.setCgst(half);
            bill.setSgst(money(gstTotal.subtract(half)));
            bill.setIgst(BigDecimal.ZERO);
        }
        bill.setGrandTotal(money(subtotal.add(gstTotal)));
    }

    private void updateBillBalanceStatus(Bill bill) {
        BigDecimal paid = money(bill.getAmountPaid());
        BigDecimal balance = money(bill.getGrandTotal().subtract(paid));
        bill.setAmountPaid(paid);
        bill.setBalanceAmount(balance);
        if (bill.getStatus() == BillStatus.CANCELLED) {
            return;
        }
        if (bill.getGrandTotal().compareTo(BigDecimal.ZERO) == 0 && paid.compareTo(BigDecimal.ZERO) == 0) {
            bill.setStatus(BillStatus.PENDING);
        } else if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setBalanceAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setStatus(bill.getBillType() == BillType.ONGOING ? BillStatus.FULLY_PAID : BillStatus.PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        } else {
            bill.setStatus(BillStatus.PENDING);
        }
    }

    private void ensureEditableOngoingBill(Bill bill) {
        if (bill.getBillType() != BillType.ONGOING) {
            throw new ResponseStatusException(BAD_REQUEST, "Final bills cannot be edited");
        }
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Cancelled bill cannot be edited");
        }
    }

    private void addStockTransaction(Part part, Bill bill, String type, int change, int before, int after, String note) {
        StockTransaction tx = new StockTransaction();
        tx.setPart(part);
        tx.setBill(bill);
        tx.setTransactionType(type);
        tx.setQuantityChange(change);
        tx.setStockBefore(before);
        tx.setStockAfter(after);
        tx.setNote(note);
        tx.setCreatedBy("system");
        stockTransactions.save(tx);
    }

    private String nextBillNumber() {
        String prefix = "BILL-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        long countToday = bills.countByCreatedAtAfter(LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        return prefix + String.format("%04d", countToday + 1);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String blankOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static BigDecimal sanitizeMoney(BigDecimal value) {
        return money(value == null ? BigDecimal.ZERO : value);
    }

    private static BigDecimal gstRateOrDefault(BigDecimal value) {
        return value == null ? BigDecimal.valueOf(18).setScale(2, RoundingMode.HALF_UP) : money(value);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : money(value);
    }

    private static String blank(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new EntityNotFoundException("Missing monetary value");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
