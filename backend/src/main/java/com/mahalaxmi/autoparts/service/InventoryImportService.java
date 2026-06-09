package com.mahalaxmi.autoparts.service;

import com.mahalaxmi.autoparts.api.Dtos;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.domain.Purchase;
import com.mahalaxmi.autoparts.domain.PurchaseItem;
import com.mahalaxmi.autoparts.domain.Supplier;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.PurchaseRepository;
import com.mahalaxmi.autoparts.repository.SupplierRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryImportService {
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z])\\d+(?:,\\d{2,3})*(?:\\.\\d+)?(?![A-Za-z])");
    private static final Pattern ANTARIKSH_ROW_START = Pattern.compile("^(\\d{1,4})\\s+([A-Za-z0-9/-]{5,})\\s+([A-Za-z0-9]{1,8})\\s+(.+)$");
    private static final Pattern ANTARIKSH_ROW_TAIL = Pattern.compile(
            "^(.*?)\\s+(\\d{6,10})(?:\\s+\\d{6,10})?\\s+(?:\\d+(?:\\.\\d+)?%?\\s+){2,3}(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:,\\d{2,3})*(?:\\.\\d+)?)\\s+\\d+(?:\\.\\d+)?\\s+(\\d+(?:,\\d{2,3})*(?:\\.\\d+)?)$"
    );
    private static final Pattern DESCRIPTION_HSN_ROW = Pattern.compile(
            "^(\\d{1,4})\\s+(.+)\\s+(\\d{4,10})\\s+(\\d+(?:\\.\\d+)?)\\s+[A-Za-z]+\\s+(\\d+(?:,\\d{2,3})*(?:\\.\\d+)?)\\s+[A-Za-z]+\\s+\\d+(?:\\.\\d+)?\\s*%\\s+(\\d+(?:,\\d{2,3})*(?:\\.\\d+)?)$"
    );
    private static final Pattern OM_SAI_ROW_START = Pattern.compile("^(\\d{1,4})\\s+(.+)$");
    private static final Pattern OM_SAI_ROW_TAIL = Pattern.compile(
            "^(.*?)\\s+(\\d+(?:,\\d{2,3})*\\.\\d{2})PCS(\\d+(?:,\\d{2,3})*\\.\\d{2})\\d+(?:,\\d{2,3})*\\.\\d{2}(\\d+)\\s+PCS(\\d{4,10})$"
    );
    private static final Pattern OM_SAI_SEPARATE_ROW_TAIL = Pattern.compile(
            "^(.*?)\\s+(\\d{4,10})\\s+(\\d+(?:\\.\\d+)?)\\s+PCS\\s+\\d+(?:,\\d{2,3})*\\.\\d{2}\\s+(\\d+(?:,\\d{2,3})*\\.\\d{2})\\s+PCS\\s+(\\d+(?:,\\d{2,3})*\\.\\d{2})$"
    );

    private final PartRepository parts;
    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;

    public InventoryImportService(PartRepository parts, PurchaseRepository purchases, SupplierRepository suppliers) {
        this.parts = parts;
        this.purchases = purchases;
        this.suppliers = suppliers;
    }

    public List<Dtos.InventoryImportRow> preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a dealer invoice PDF");
        }
        try (var document = Loader.loadPDF(file.getBytes())) {
            return parseText(new PDFTextStripper().getText(document));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read PDF invoice", ex);
        }
    }

    public List<Dtos.InventoryImportRow> parseText(String text) {
        List<Dtos.InventoryImportRow> omSaiRows = parseOmSaiText(text);
        if (!omSaiRows.isEmpty()) {
            return omSaiRows;
        }
        List<Dtos.InventoryImportRow> descriptionRows = parseDescriptionHsnText(text);
        if (!descriptionRows.isEmpty()) {
            return descriptionRows;
        }
        List<Dtos.InventoryImportRow> antarikshRows = parseAntarikshText(text);
        if (!antarikshRows.isEmpty()) {
            return antarikshRows;
        }
        List<Dtos.InventoryImportRow> rows = new ArrayList<>();
        StringBuilder carry = new StringBuilder();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace('\t', ' ').replaceAll("\\s+", " ").trim();
            if (line.isBlank() || isNoise(line)) {
                continue;
            }
            ParsedRow parsed = parseRow(line);
            if (parsed == null) {
                if (looksLikeDescription(line)) {
                    if (!carry.isEmpty()) {
                        carry.append(' ');
                    }
                    carry.append(line);
                }
                continue;
            }
            String itemName = parsed.itemName();
            if (!carry.isEmpty()) {
                itemName = carry + " " + itemName;
                carry.setLength(0);
            }
            rows.add(new Dtos.InventoryImportRow(
                    null,
                    itemName.trim(),
                    parsed.partNumber(),
                    parsed.hsnCode(),
                    "",
                    parsed.quantity(),
                    parsed.rate(),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2),
                    parsed.amount(),
                    isDuplicate(parsed.partNumber(), itemName),
                    line
            ));
        }
        if (rows.isEmpty()) {
            return parseColumnarText(text);
        }
        return rows;
    }

    private List<Dtos.InventoryImportRow> parseOmSaiText(String text) {
        List<Dtos.InventoryImportRow> rows = new ArrayList<>();
        OmSaiRowBuilder current = null;
        boolean waitingForDescription = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace('\t', ' ').replaceAll("\\s+", " ").trim();
            if (line.isBlank() || isOmSaiNoise(line)) {
                continue;
            }
            if (line.matches("\\d{1,4}") && lineHasRowNumber(line)) {
                if (current != null) {
                    addOmSaiRow(rows, current);
                }
                current = null;
                waitingForDescription = true;
                continue;
            }
            Matcher start = OM_SAI_ROW_START.matcher(line);
            if (!waitingForDescription && start.matches() && lineHasRowNumber(start.group(1))) {
                if (current != null) {
                    addOmSaiRow(rows, current);
                }
                current = new OmSaiRowBuilder(start.group(2));
                if (addOmSaiRow(rows, current)) {
                    current = null;
                }
                continue;
            }
            if (waitingForDescription) {
                current = new OmSaiRowBuilder(line);
                waitingForDescription = false;
                if (addOmSaiRow(rows, current)) {
                    current = null;
                }
                continue;
            }
            if (current != null) {
                current.text.append(' ').append(line);
                if (addOmSaiRow(rows, current)) {
                    current = null;
                }
            }
        }
        if (current != null) {
            addOmSaiRow(rows, current);
        }
        return rows;
    }

    private boolean addOmSaiRow(List<Dtos.InventoryImportRow> rows, OmSaiRowBuilder current) {
        Matcher matcher = OM_SAI_ROW_TAIL.matcher(current.text.toString().trim());
        if (matcher.matches()) {
            return addOmSaiRow(
                    rows,
                    matcher.group(1),
                    matcher.group(5),
                    matcher.group(4),
                    matcher.group(3),
                    matcher.group(2)
            );
        }
        Matcher separate = OM_SAI_SEPARATE_ROW_TAIL.matcher(current.text.toString().trim());
        if (separate.matches()) {
            return addOmSaiRow(
                    rows,
                    separate.group(1),
                    separate.group(2),
                    separate.group(3),
                    separate.group(4),
                    separate.group(5)
            );
        }
        return false;
    }

    private boolean addOmSaiRow(List<Dtos.InventoryImportRow> rows, String rawName, String hsnCode, String quantityText, String rateText, String amountText) {
        String itemName = rawName.trim();
        if (itemName.isBlank()) {
            return false;
        }
        BigDecimal quantity = new BigDecimal(quantityText.replace(",", ""));
        rows.add(new Dtos.InventoryImportRow(
                null,
                itemName,
                "",
                hsnCode,
                "",
                quantity.setScale(0, RoundingMode.DOWN).intValue(),
                money(new BigDecimal(rateText.replace(",", ""))),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                money(new BigDecimal(amountText.replace(",", ""))),
                isDuplicate(null, itemName),
                "om-sai-pdf"
        ));
        return true;
    }

    private boolean lineHasRowNumber(String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 && number < 10000;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isOmSaiNoise(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("TAX INVOICE")
                || upper.startsWith("OM SAI")
                || upper.startsWith("MODI STEEL")
                || upper.startsWith("BEHIND ")
                || upper.startsWith("SARDA ")
                || upper.startsWith("GSTIN")
                || upper.startsWith("STATE NAME")
                || upper.startsWith("CONSIGNEE")
                || upper.startsWith("BUYER")
                || upper.startsWith("MAHALAXMI")
                || upper.startsWith("INVOICE NO")
                || upper.startsWith("DELIVERY")
                || upper.startsWith("REFERENCE")
                || upper.startsWith("BUYER'S")
                || upper.startsWith("DISPATCH")
                || upper.startsWith("DISPATCHED")
                || upper.startsWith("DATED")
                || upper.startsWith("MODE/")
                || upper.startsWith("OTHER REFERENCES")
                || upper.startsWith("DESTINATION")
                || upper.startsWith("TERMS")
                || upper.startsWith("SL DESCRIPTION")
                || upper.startsWith("NO. ")
                || upper.startsWith("DESCRIPTION OF GOODS")
                || upper.equals("RATE")
                || upper.startsWith("(INCL. OF TAX)")
                || upper.startsWith("RATE PER")
                || upper.startsWith("CONTINUED")
                || upper.startsWith("SUBJECT ")
                || upper.startsWith("THIS IS")
                || upper.startsWith("OUTPUT ")
                || upper.startsWith("ROUND OFF")
                || upper.startsWith("TOTAL ")
                || upper.startsWith("AMOUNT ")
                || upper.startsWith("DECLARATION")
                || upper.startsWith("WE DECLARE")
                || upper.startsWith("E. & O.E")
                || upper.startsWith("COMPANY'S")
                || upper.startsWith("BANK NAME")
                || upper.startsWith("A/C NO")
                || upper.startsWith("BRANCH")
                || upper.startsWith("FOR OM SAI")
                || upper.startsWith("AUTHORISED")
                || upper.startsWith("HSN/SAC")
                || upper.startsWith("TAX AMOUNT")
                || upper.startsWith("PARTY :");
    }

    private List<Dtos.InventoryImportRow> parseDescriptionHsnText(String text) {
        List<Dtos.InventoryImportRow> rows = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace('\t', ' ').replaceAll("\\s+", " ").trim();
            if (line.isBlank() || isDescriptionHsnNoise(line)) {
                continue;
            }
            Matcher matcher = DESCRIPTION_HSN_ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String itemName = matcher.group(2).trim();
            rows.add(new Dtos.InventoryImportRow(
                    null,
                    itemName,
                    "",
                    matcher.group(3),
                    "",
                    new BigDecimal(matcher.group(4).replace(",", "")).setScale(0, RoundingMode.DOWN).intValue(),
                    money(new BigDecimal(matcher.group(5).replace(",", ""))),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2),
                    money(new BigDecimal(matcher.group(6).replace(",", ""))),
                    isDuplicate(null, itemName),
                    "description-hsn-pdf"
            ));
        }
        return rows;
    }

    private boolean isDescriptionHsnNoise(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("SL")
                || upper.equals("NO.")
                || upper.startsWith("DESCRIPTION OF GOODS")
                || upper.startsWith("CONTINUED")
                || upper.startsWith("TOTAL ")
                || upper.startsWith("OUTPUT CGST")
                || upper.startsWith("OUTPUT SGST")
                || upper.startsWith("ROUND OFF")
                || upper.contains("E. & O.E");
    }

    private List<Dtos.InventoryImportRow> parseAntarikshText(String text) {
        List<Dtos.InventoryImportRow> rows = new ArrayList<>();
        AntarikshRowBuilder current = null;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace('\t', ' ').replaceAll("\\s+", " ").trim();
            if (line.isBlank() || isAntarikshNoise(line)) {
                continue;
            }
            Matcher start = ANTARIKSH_ROW_START.matcher(line);
            if (start.matches()) {
                if (current != null) {
                    addAntarikshRow(rows, current);
                }
                current = new AntarikshRowBuilder(start.group(1), start.group(2), start.group(4));
                if (addAntarikshRow(rows, current)) {
                    current = null;
                }
                continue;
            }
            if (current != null) {
                current.description.append(' ').append(line);
                if (addAntarikshRow(rows, current)) {
                    current = null;
                }
            }
        }
        if (current != null) {
            addAntarikshRow(rows, current);
        }
        return rows;
    }

    private boolean addAntarikshRow(List<Dtos.InventoryImportRow> rows, AntarikshRowBuilder current) {
        Matcher tail = ANTARIKSH_ROW_TAIL.matcher(current.description.toString().trim());
        if (!tail.matches()) {
            return false;
        }
        String itemName = tail.group(1).trim();
        if (itemName.isBlank()) {
            return false;
        }
        BigDecimal quantity = new BigDecimal(tail.group(3).replace(",", ""));
        rows.add(new Dtos.InventoryImportRow(
                null,
                itemName,
                current.partNumber.toUpperCase(),
                tail.group(2),
                "",
                quantity.setScale(0, RoundingMode.DOWN).intValue(),
                money(new BigDecimal(tail.group(4).replace(",", ""))),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                money(new BigDecimal(tail.group(5).replace(",", ""))),
                isDuplicate(current.partNumber, itemName),
                "auto-antariksh-pdf"
        ));
        return true;
    }

    private boolean isAntarikshNoise(String line) {
        String upper = line.toUpperCase();
        return upper.equals("SRL. PART")
                || upper.equals("NUMBER")
                || upper.equals("BATCH DESCRIPTION HSN CGST SGST/")
                || upper.equals("UTGST")
                || upper.equals("QTY RATE DISC")
                || upper.equals("%")
                || upper.equals("TAXABLE")
                || upper.equals("AMOUNT")
                || upper.startsWith("REMARKS")
                || upper.startsWith("NO.OF ITEMS")
                || upper.startsWith("CUSTOMER SIGNATURE")
                || upper.startsWith("FOR AUTO ANTARIKSH")
                || upper.startsWith("PART SUB TOTAL")
                || upper.startsWith("PART DISCOUNT")
                || upper.startsWith("PART TOTAL TAXABLE")
                || upper.startsWith("CGST @")
                || upper.startsWith("SGST @")
                || upper.startsWith("SUB TOTAL")
                || upper.startsWith("NET BILL")
                || upper.startsWith("RUPEES ");
    }

    private List<Dtos.InventoryImportRow> parseColumnarText(String text) {
        List<String> lines = text.lines()
                .map(line -> line.replace('\t', ' ').replaceAll("\\s+", " ").trim())
                .filter(line -> !line.isBlank())
                .toList();
        List<Dtos.InventoryImportRow> rows = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            List<String> serials = readSerialBlock(lines, index);
            if (serials.size() < 2) {
                index++;
                continue;
            }
            int count = serials.size();
            int cursor = index + count;
            if (!hasBlock(lines, cursor, count, this::isPartNumberLine)) {
                index++;
                continue;
            }
            List<String> partNumbers = lines.subList(cursor, cursor + count);
            cursor += count;
            if (!hasBlock(lines, cursor, count, this::isDecimalLine)) {
                index++;
                continue;
            }
            List<String> rates = lines.subList(cursor, cursor + count);
            cursor += count;
            if (hasBlock(lines, cursor, count, this::isDecimalLine)) {
                cursor += count;
            }
            int quantityStart = findQuantityBlock(lines, cursor, count);
            if (quantityStart < 0) {
                index++;
                continue;
            }
            List<String> descriptionLines = lines.subList(cursor, quantityStart).stream()
                    .filter(line -> !isColumnNoise(line))
                    .toList();
            List<String> hsnCodes = List.of();
            if (descriptionLines.size() >= count && hasBlock(descriptionLines, descriptionLines.size() - count, count, this::isHsnLine)) {
                hsnCodes = descriptionLines.subList(descriptionLines.size() - count, descriptionLines.size());
                descriptionLines = descriptionLines.subList(0, descriptionLines.size() - count);
            }
            List<String> descriptions = mergeDescriptions(descriptionLines, count);
            List<String> quantities = lines.subList(quantityStart, quantityStart + count);
            List<String> amounts = lines.subList(quantityStart + count, quantityStart + (2 * count));
            for (int row = 0; row < count; row++) {
                String itemName = row < descriptions.size() ? descriptions.get(row) : partNumbers.get(row);
                rows.add(new Dtos.InventoryImportRow(
                        null,
                        itemName,
                        partNumbers.get(row).toUpperCase(),
                        row < hsnCodes.size() ? hsnCodes.get(row) : "",
                        "",
                        Integer.parseInt(quantities.get(row)),
                        money(new BigDecimal(rates.get(row).replace(",", ""))),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        money(new BigDecimal(amounts.get(row).replace(",", ""))),
                        isDuplicate(partNumbers.get(row), itemName),
                        "columnar-pdf"
                ));
            }
            index = quantityStart + (2 * count);
        }
        return rows;
    }

    private List<String> readSerialBlock(List<String> lines, int start) {
        List<String> serials = new ArrayList<>();
        int previous = -1;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.matches("\\d{1,4}")) {
                break;
            }
            int value = Integer.parseInt(line);
            if (previous != -1 && value != previous + 1) {
                break;
            }
            serials.add(line);
            previous = value;
        }
        return serials;
    }

    private boolean hasBlock(List<String> lines, int start, int count, java.util.function.Predicate<String> predicate) {
        if (start < 0 || start + count > lines.size()) {
            return false;
        }
        for (int i = start; i < start + count; i++) {
            if (!predicate.test(lines.get(i))) {
                return false;
            }
        }
        return true;
    }

    private int findQuantityBlock(List<String> lines, int start, int count) {
        for (int i = start; i + (2 * count) <= lines.size(); i++) {
            if (hasBlock(lines, i, count, line -> line.matches("\\d{1,4}"))
                    && hasBlock(lines, i + count, count, this::isDecimalLine)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> mergeDescriptions(List<String> lines, int count) {
        List<String> descriptions = new ArrayList<>();
        for (String line : lines) {
            if (!descriptions.isEmpty() && isDescriptionContinuation(descriptions.get(descriptions.size() - 1), line, lines.size(), count)) {
                int last = descriptions.size() - 1;
                descriptions.set(last, (descriptions.get(last) + " " + line).trim());
            } else if (descriptions.size() < count) {
                descriptions.add(line);
            } else {
                int last = descriptions.size() - 1;
                descriptions.set(last, (descriptions.get(last) + " " + line).trim());
            }
        }
        return descriptions;
    }

    private boolean isDescriptionContinuation(String previous, String line, int rawLineCount, int expectedCount) {
        if (line.startsWith("&") || line.length() <= 4) {
            return true;
        }
        String previousUpper = previous.toUpperCase();
        String lineUpper = line.toUpperCase();
        boolean shortWrappedWord = lineUpper.matches("[A-Z][A-Z0-9/-]{1,10}");
        boolean previousLooksOpen = previous.endsWith(",")
                || previousUpper.endsWith(" WITH")
                || previousUpper.endsWith(" WITH,")
                || previousUpper.contains(",WITH");
        return rawLineCount > expectedCount && shortWrappedWord && previousLooksOpen;
    }

    private boolean isPartNumberLine(String line) {
        return line.matches("(?=.*\\d)[A-Za-z0-9/-]{5,}");
    }

    private boolean isDecimalLine(String line) {
        return line.matches("\\d+(?:,\\d{2,3})*(?:\\.\\d+)?");
    }

    private boolean isHsnLine(String line) {
        return line.matches("\\d{6,10}");
    }

    private boolean isColumnNoise(String line) {
        String upper = line.toUpperCase();
        return upper.equals("CUSTOMER DETAILS") || upper.equals("DATE :") || upper.equals("CODE")
                || upper.startsWith("SRL.") || upper.equals("AMOUNT") || upper.equals("NET")
                || upper.equals(":") || upper.equals("CONTd..".toUpperCase());
    }

    @Transactional
    public List<Dtos.PartResponse> saveImport(Dtos.InventoryImportSaveRequest request) {
        List<Dtos.PartResponse> saved = new ArrayList<>();
        Purchase purchase = request.recordPurchase() ? newPurchaseRecord(request) : null;
        for (Dtos.InventoryImportRow row : request.rows()) {
            validate(row);
            Part part = findDuplicate(row);
            if (part != null && !request.allowOverride()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate inventory item: " + row.itemName());
            }
            if (part == null) {
                part = new Part();
                part.setPartNumber(clean(row.partNumber()));
                part.setCarCompatibility("Imported from dealer PDF");
                part.setWarehouseLocation("Main Warehouse");
                part.setGstRate(BigDecimal.valueOf(18));
            }
            part.setName(row.itemName().trim());
            part.setActive(true);
            part.setHsnCode(trimToNull(row.hsnCode()));
            part.setRackNumber(trimToNull(row.rackNumber()));
            part.setStockLevel(row.quantity());
            part.setSellingPrice(money(row.sellingPrice()));
            part.setPurchaseDiscount(money(row.purchaseDiscount()));
            part.setCostPrice(resolvePurchasePrice(row));
            Part savedPart = parts.save(part);
            if (purchase != null) {
                purchase.addItem(purchaseItem(savedPart, row));
            }
            saved.add(com.mahalaxmi.autoparts.api.ApiMapper.part(savedPart));
        }
        if (purchase != null) {
            updatePurchaseTotals(purchase);
            purchases.save(purchase);
        }
        return saved;
    }

    private void updatePurchaseTotals(Purchase purchase) {
        BigDecimal subtotal = purchase.getItems().stream()
                .map(PurchaseItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        purchase.setSubtotal(money(subtotal));
        purchase.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        purchase.setGrandTotal(money(subtotal));
    }

    private Purchase newPurchaseRecord(Dtos.InventoryImportSaveRequest request) {
        Supplier supplier = supplierFor(request.supplierName());
        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setDealerInvoiceNumber(trimToNull(request.dealerInvoiceNumber()));
        purchase.setPurchaseDate(request.purchaseDate() == null ? LocalDate.now() : request.purchaseDate());
        purchase.setNotes(trimToNull(request.notes()));
        return purchase;
    }

    private PurchaseItem purchaseItem(Part part, Dtos.InventoryImportRow row) {
        BigDecimal unitCost = resolvePurchasePrice(row);
        BigDecimal lineTotal = money(row.sellingPrice().multiply(BigDecimal.valueOf(row.quantity())));
        PurchaseItem item = new PurchaseItem();
        item.setPart(part);
        item.setPartName(part.getName());
        item.setPartNumber(part.getPartNumber() == null ? "" : part.getPartNumber());
        item.setQuantity(row.quantity());
        item.setUnitCost(unitCost);
        item.setDiscountPercentage(money(row.purchaseDiscount()));
        item.setLineTotal(lineTotal);
        return item;
    }

    private Supplier supplierFor(String supplierName) {
        String name = supplierName == null || supplierName.trim().isBlank() ? "Dealer PDF Supplier" : supplierName.trim();
        return suppliers.findByName(name).orElseGet(() -> {
            Supplier supplier = new Supplier();
            supplier.setName(name);
            supplier.setDefaultDiscount(BigDecimal.ZERO);
            return suppliers.save(supplier);
        });
    }

    private void validate(Dtos.InventoryImportRow row) {
        if (row.itemName() == null || row.itemName().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item name is required");
        }
        if (row.quantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity cannot be negative");
        }
        if (row.sellingPrice() == null || row.sellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price must be greater than zero");
        }
        if (row.purchasePrice() == null || row.purchasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase price cannot be negative");
        }
        if (row.purchaseDiscount() == null || row.purchaseDiscount().compareTo(BigDecimal.ZERO) < 0 || row.purchaseDiscount().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase discount must be between 0 and 100");
        }
    }

    private Part findDuplicate(Dtos.InventoryImportRow row) {
        String partNumber = clean(row.partNumber());
        if (partNumber != null) {
            var byNumber = parts.findByPartNumber(partNumber);
            if (byNumber.isPresent()) {
                return byNumber.get();
            }
        }
        return parts.findByNameIgnoreCaseOrderByIdAsc(row.itemName().trim()).stream().findFirst().orElse(null);
    }

    private boolean isDuplicate(String partNumber, String itemName) {
        String cleanedPartNumber = clean(partNumber);
        if (cleanedPartNumber != null && parts.findByPartNumberAndActiveTrue(cleanedPartNumber).isPresent()) {
            return true;
        }
        return !parts.findByNameIgnoreCaseAndActiveTrueOrderByIdAsc(itemName.trim()).isEmpty();
    }

    private BigDecimal resolvePurchasePrice(Dtos.InventoryImportRow row) {
        BigDecimal direct = money(row.purchasePrice());
        if (direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }
        BigDecimal discount = money(row.purchaseDiscount());
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return money(row.sellingPrice().subtract(row.sellingPrice().multiply(discount).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
    }

    private ParsedRow parseRow(String line) {
        List<Num> nums = numbers(line);
        if (nums.size() < 3) {
            return null;
        }
        Num amountNum = nums.get(nums.size() - 1);
        BigDecimal amount = amountNum.value();
        for (int i = 0; i < nums.size() - 1; i++) {
            Num qtyNum = nums.get(i);
            if (qtyNum.start() == 0) {
                continue;
            }
            if (!qtyNum.text().matches("\\d+") || qtyNum.value().compareTo(BigDecimal.ZERO) <= 0 || qtyNum.value().compareTo(BigDecimal.valueOf(9999)) > 0) {
                continue;
            }
            int qty = qtyNum.value().intValue();
            for (int j = i + 1; j < nums.size(); j++) {
                BigDecimal rate = nums.get(j).value();
                if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal expected = rate.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                if (expected.subtract(amount).abs().compareTo(BigDecimal.valueOf(1)) <= 0) {
                    String left = line.substring(0, qtyNum.start()).trim();
                    Description description = description(left);
                    return new ParsedRow(description.itemName(), description.partNumber(), description.hsnCode(), qty, money(rate), money(amount));
                }
            }
        }
        return null;
    }

    private Description description(String value) {
        String[] tokens = value.split("\\s+");
        int start = 0;
        if (tokens.length > 0 && tokens[0].matches("\\d{1,4}")) {
            start = 1;
        }
        String partNumber = null;
        if (tokens.length > start && tokens[start].matches("(?=.*\\d)[A-Za-z0-9/-]{5,}")) {
            partNumber = tokens[start].toUpperCase();
            start++;
        }
        int end = tokens.length;
        String hsnCode = null;
        while (end > start && tokens[end - 1].matches("\\d{6,10}")) {
            if (hsnCode == null) {
                hsnCode = tokens[end - 1];
            }
            end--;
        }
        return new Description(String.join(" ", java.util.Arrays.copyOfRange(tokens, start, end)), partNumber, hsnCode);
    }

    private List<Num> numbers(String line) {
        List<Num> nums = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(line);
        while (matcher.find()) {
            nums.add(new Num(matcher.group(), matcher.start(), new BigDecimal(matcher.group().replace(",", ""))));
        }
        return nums;
    }

    private boolean isNoise(String line) {
        String upper = line.toUpperCase();
        return upper.contains("TOTAL") || upper.contains("GSTIN") || upper.contains("INVOICE")
                || upper.contains("BANK") || upper.contains("DECLARATION") || upper.contains("AMOUNT IN WORD");
    }

    private boolean looksLikeDescription(String line) {
        return line.matches(".*[A-Za-z]{3,}.*") && !line.matches(".*\\b(CGST|SGST|IGST|ROUNDING|LESS)\\b.*");
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Num(String text, int start, BigDecimal value) {
    }

    private record ParsedRow(String itemName, String partNumber, String hsnCode, int quantity, BigDecimal rate, BigDecimal amount) {
    }

    private record Description(String itemName, String partNumber, String hsnCode) {
    }

    private static final class AntarikshRowBuilder {
        private final String serialNo;
        private final String partNumber;
        private final StringBuilder description;

        private AntarikshRowBuilder(String serialNo, String partNumber, String description) {
            this.serialNo = serialNo;
            this.partNumber = partNumber;
            this.description = new StringBuilder(description);
        }
    }

    private static final class OmSaiRowBuilder {
        private final StringBuilder text;

        private OmSaiRowBuilder(String text) {
            this.text = new StringBuilder(text);
        }
    }
}
