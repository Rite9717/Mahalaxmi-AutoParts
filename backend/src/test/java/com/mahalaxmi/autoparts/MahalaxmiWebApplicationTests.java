package com.mahalaxmi.autoparts;

import com.mahalaxmi.autoparts.api.Dtos;
import com.mahalaxmi.autoparts.domain.InvoiceType;
import com.mahalaxmi.autoparts.domain.Supplier;
import com.mahalaxmi.autoparts.domain.SupplyType;
import com.mahalaxmi.autoparts.repository.BillRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.PurchaseRepository;
import com.mahalaxmi.autoparts.repository.StockTransactionRepository;
import com.mahalaxmi.autoparts.repository.SupplierRepository;
import com.mahalaxmi.autoparts.service.InventoryImportService;
import com.mahalaxmi.autoparts.service.InventoryService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MahalaxmiWebApplicationTests {
    @Autowired
    private PartRepository parts;

    @Autowired
    private BillRepository bills;

    @Autowired
    private StockTransactionRepository transactions;

    @Autowired
    private PurchaseRepository purchases;

    @Autowired
    private SupplierRepository suppliers;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private InventoryImportService imports;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        transactions.deleteAll();
        bills.deleteAll();
        purchases.deleteAll();
        parts.deleteAll();
        suppliers.deleteAll();
    }

    @Test
    void appStartsWithEmptyInventoryWhenNoManualDataExists() {
        assertThat(parts.findAll()).isEmpty();
    }

    @Test
    void pdfParserMergesCarriedDescriptionLinesAndUsesRateAsSellingPrice() {
        String text = """
                BUSH REAR SUSPENSION
                HEAVY DUTY MODEL
                1 BR12345 87089900 2 500.00 450.00 18.00 900.00
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).itemName()).isEqualTo("BUSH REAR SUSPENSION HEAVY DUTY MODEL");
        assertThat(rows.get(0).hsnCode()).isEqualTo("87089900");
        assertThat(rows.get(0).quantity()).isEqualTo(2);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("450.00");
    }

    @Test
    void columnarPdfParserKeepsWrappedDescriptionWithCorrectPartNumber() {
        String text = """
                38
                39
                22011M79G41
                09482M00551
                2258.500
                78.000
                0.00
                0.00
                CLUTCH SET,ALTO,MODIFIED,WITH
                DAMPE
                SPARK PLUG,BKR6E
                2
                10
                4517.00
                780.00
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).partNumber()).isEqualTo("22011M79G41");
        assertThat(rows.get(0).itemName()).isEqualTo("CLUTCH SET,ALTO,MODIFIED,WITH DAMPE");
        assertThat(rows.get(0).quantity()).isEqualTo(2);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("2258.50");
        assertThat(rows.get(1).partNumber()).isEqualTo("09482M00551");
        assertThat(rows.get(1).itemName()).isEqualTo("SPARK PLUG,BKR6E");
    }

    @Test
    void autoAntarikshPdfParserReadsHsnAndWrappedDescriptions() {
        String text = """
                Srl. Part
                Number
                Batch Description HSN CGST SGST/
                UTGST
                Qty Rate Disc
                %
                Taxable
                Amount
                18 82850M60M00 AJ SHAFT,BACK DOOR HANDLE 87089900 9% 9% 2.00 140.250 8.00 280.50
                19 83401M82P11 AH REGULATOR ASSY,FRONT
                WINDOW,RH
                87089900 9% 9% 1.00 480.080 8.00 480.08
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).serialNo()).isNull();
        assertThat(rows.get(0).partNumber()).isEqualTo("82850M60M00");
        assertThat(rows.get(0).itemName()).isEqualTo("SHAFT,BACK DOOR HANDLE");
        assertThat(rows.get(0).hsnCode()).isEqualTo("87089900");
        assertThat(rows.get(0).quantity()).isEqualTo(2);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("140.25");
        assertThat(rows.get(0).totalAmount()).isEqualByComparingTo("280.50");
        assertThat(rows.get(1).itemName()).isEqualTo("REGULATOR ASSY,FRONT WINDOW,RH");
    }

    @Test
    void kopragaonGstPdfParserReadsThreeTaxColumnsAndRepeatedHsn() {
        String text = """
                Srl. Part Number Batch Description HSN CGST
                SGST/
                UTGST
                Qty Rate Disc % Taxable Amount
                1 01550M1040A AC BOLT 87089900 9% 9% 9% 6.00 9.320 8.00 55.92
                14 09356M75141-500 AH HOSE WATER BY PASS(FB8 MPFI) 40092200 40092200 9% 9% 9% 10.00 13.130 8.00 131.30
                201 99000M24120-331 AJ LIQUID GASKET(THREE BOND-1215) 100 35069999 9% 9% 9% 1.00 292.370 8.00 292.37
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).partNumber()).isEqualTo("01550M1040A");
        assertThat(rows.get(0).itemName()).isEqualTo("BOLT");
        assertThat(rows.get(0).hsnCode()).isEqualTo("87089900");
        assertThat(rows.get(0).quantity()).isEqualTo(6);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("9.32");
        assertThat(rows.get(0).totalAmount()).isEqualByComparingTo("55.92");
        assertThat(rows.get(1).itemName()).isEqualTo("HOSE WATER BY PASS(FB8 MPFI)");
        assertThat(rows.get(1).hsnCode()).isEqualTo("40092200");
        assertThat(rows.get(2).itemName()).isEqualTo("LIQUID GASKET(THREE BOND-1215) 100");
        assertThat(rows.get(2).hsnCode()).isEqualTo("35069999");
    }

    @Test
    void descriptionHsnPdfParserLeavesPartNumberBlank() {
        String text = """
                Sl
                No.
                Description of Goods HSN/SAC Quantity Rate per Disc. % Amount
                1 OIL FILTER SWIFT 84212300 10 PCS 187.00 PCS 32 % 1,271.60
                31 OIL FILTER FORD ECOSPORT N/M 8512 2 PCS 354.00 PCS 32 % 481.44
                42 REAR SHOCKUP OLD SWIFT 870880 2 PCS 1,597.00 PCS 23 % 2,459.38
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).partNumber()).isBlank();
        assertThat(rows.get(0).itemName()).isEqualTo("OIL FILTER SWIFT");
        assertThat(rows.get(0).hsnCode()).isEqualTo("84212300");
        assertThat(rows.get(0).quantity()).isEqualTo(10);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("187.00");
        assertThat(rows.get(0).totalAmount()).isEqualByComparingTo("1271.60");
        assertThat(rows.get(1).itemName()).isEqualTo("OIL FILTER FORD ECOSPORT N/M");
        assertThat(rows.get(1).hsnCode()).isEqualTo("8512");
        assertThat(rows.get(2).sellingPrice()).isEqualByComparingTo("1597.00");
    }

    @Test
    void omSaiPdfParserReadsGluedAmountRateQuantityHsnLayout() {
        String text = """
                Sl Description of Goods AmountperRateRateQuantityHSN/SAC
                No. (Incl. of Tax)
                1 V 843922-CRB HYUDAI EON/GRAND I10/I10
                /I20 Act
                1,130.40PCS471.00555.783 PCS84828000
                32 FRONT STABLIZER LINK ASSY OLD SWIFT
                /DZIRE
                1,437.12PCS998.001,177.642 PCS87089900
                39 RACK END BALENO 799.20PCS555.00654.902 PCS87089900
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).partNumber()).isBlank();
        assertThat(rows.get(0).itemName()).isEqualTo("V 843922-CRB HYUDAI EON/GRAND I10/I10 /I20 Act");
        assertThat(rows.get(0).hsnCode()).isEqualTo("84828000");
        assertThat(rows.get(0).quantity()).isEqualTo(3);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("471.00");
        assertThat(rows.get(0).totalAmount()).isEqualByComparingTo("1130.40");
        assertThat(rows.get(1).itemName()).isEqualTo("FRONT STABLIZER LINK ASSY OLD SWIFT /DZIRE");
        assertThat(rows.get(1).quantity()).isEqualTo(2);
        assertThat(rows.get(2).itemName()).isEqualTo("RACK END BALENO");
    }

    @Test
    void omSaiPdfParserIgnoresHeaderWhenRowNumberIsOnSeparateLine() {
        String text = """
                TAX INVOICE
                Om Sai Auto LLP 26-27 Invoice No. OSA/26-27/0047
                Modi Steel Basement, Renuka Nagar, Delivery Note
                Behind National Urdu High School Reference No. & Date.
                Sl
                No.
                Description of Goods HSN/SAC Quantity
                Rate
                (Incl. of Tax)
                Rate per Amount
                1
                RADIATOR ALTO/WAGON R
                87089900 1 PCS 2,459.54 2,083.00 PCS 1,562.25
                2
                IRL1084 RADIATOR SWIFT DZ DSL 16MM
                87089100 1 PCS 4,366.00 3,700.00 PCS 2,516.00
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).itemName()).isEqualTo("RADIATOR ALTO/WAGON R");
        assertThat(rows.get(0).hsnCode()).isEqualTo("87089900");
        assertThat(rows.get(0).quantity()).isEqualTo(1);
        assertThat(rows.get(0).sellingPrice()).isEqualByComparingTo("2083.00");
        assertThat(rows.get(0).totalAmount()).isEqualByComparingTo("1562.25");
        assertThat(rows).noneMatch(row -> row.itemName().contains("Modi Steel"));
    }

    @Test
    @Transactional
    void importPreviewDoesNotFailWhenMultipleActivePartsHaveSameName() {
        createPart("SHAFT,BACK DOOR HANDLE", "DUP-001", 1, 10, 20);
        createPart("SHAFT,BACK DOOR HANDLE", "DUP-002", 1, 10, 20);
        String text = """
                18 82850M60M00 AJ SHAFT,BACK DOOR HANDLE 87089900 9% 9% 2.00 140.250 8.00 280.50
                """;

        var rows = imports.parseText(text);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).duplicate()).isTrue();
    }

    @Test
    @Transactional
    void importSaveCreatesInventoryWithPurchasePrice() {
        imports.saveImport(new Dtos.InventoryImportSaveRequest(false, false, null, null, null, null, List.of(new Dtos.InventoryImportRow(
                "1",
                "SPARK PLUG BKR6E",
                "09482M00551",
                "85111000",
                "R1",
                10,
                BigDecimal.valueOf(78),
                BigDecimal.valueOf(60),
                BigDecimal.ZERO,
                BigDecimal.valueOf(780),
                false,
                ""
        ))));

        var part = parts.findByPartNumber("09482M00551").orElseThrow();
        assertThat(part.getStockLevel()).isEqualTo(10);
        assertThat(part.getSellingPrice()).isEqualByComparingTo("78.00");
        assertThat(part.getCostPrice()).isEqualByComparingTo("60.00");
    }

    @Test
    @Transactional
    void importSaveCanCreateInventoryWithoutPartNumber() {
        imports.saveImport(new Dtos.InventoryImportSaveRequest(false, false, null, null, null, null, List.of(new Dtos.InventoryImportRow(
                null,
                "OIL FILTER SWIFT",
                "",
                "84212300",
                "",
                10,
                BigDecimal.valueOf(187),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1271.60),
                false,
                ""
        ))));

        var part = parts.findByNameIgnoreCaseOrderByIdAsc("OIL FILTER SWIFT").get(0);
        assertThat(part.getPartNumber()).isNull();
        assertThat(part.getHsnCode()).isEqualTo("84212300");
    }

    @Test
    @Transactional
    void billCreationDeductsStockAndNormalBillDoesNotRequireGstin() {
        var part = createPart("Oil Filter", "OF-001", 5, 180, 280);

        var bill = inventory.createBill(new Dtos.BillRequest(
                "Counter Sale",
                "",
                "",
                "9999999999",
                InvoiceType.NORMAL,
                LocalDate.now(),
                SupplyType.INTRA_STATE,
                "CASH",
                "",
                List.of(new Dtos.BillItemRequest(part.getId(), 2, BigDecimal.ZERO))
        ));

        assertThat(part.getStockLevel()).isEqualTo(3);
        assertThat(bill.getInvoiceType()).isEqualTo(InvoiceType.NORMAL);
        assertThat(bill.getGstTotal()).isEqualByComparingTo("0.00");
        assertThat(transactions.findAll()).anyMatch(tx -> tx.getBill() != null && tx.getBill().getId().equals(bill.getId()));
    }

    @Test
    @Transactional
    void billCreationHandlesOldPartsWithMissingPurchasePriceAndGstRate() {
        var part = createPart("Old Imported Part", "OLD-001", 3, 0, 442);
        entityManager.flush();
        entityManager.clear();
        jdbc.execute("alter table part alter column cost_price drop not null");
        jdbc.execute("alter table part alter column gst_rate drop not null");
        jdbc.update("update part set cost_price = null, gst_rate = null where id = ?", part.getId());

        var bill = inventory.createBill(new Dtos.BillRequest(
                "Counter Sale",
                "",
                "",
                "",
                InvoiceType.GST,
                LocalDate.now(),
                SupplyType.INTRA_STATE,
                "CASH",
                "",
                List.of(new Dtos.BillItemRequest(part.getId(), 1, BigDecimal.ZERO))
        ));

        assertThat(bill.getGrandTotal()).isEqualByComparingTo("442.00");
        assertThat(bill.getGstTotal()).isEqualByComparingTo("67.42");
        assertThat(bill.getCgst()).isEqualByComparingTo("33.71");
        assertThat(bill.getSgst()).isEqualByComparingTo("33.71");
        assertThat(bill.getItems().get(0).getUnitCost()).isEqualByComparingTo("0.00");
        assertThat(bill.getItems().get(0).getGstRate()).isEqualByComparingTo("18.00");
    }

    @Test
    @Transactional
    void billCreationWorksWhenPartHasNoPartNumber() {
        var part = createPart("Local Clip", null, 2, 10, 30);

        var bill = inventory.createBill(new Dtos.BillRequest(
                "Counter Sale",
                "",
                "",
                "",
                InvoiceType.NORMAL,
                LocalDate.now(),
                SupplyType.INTRA_STATE,
                "CASH",
                "",
                List.of(new Dtos.BillItemRequest(part.getId(), 1, BigDecimal.ZERO))
        ));

        assertThat(bill.getItems().get(0).getPartNumber()).isBlank();
        assertThat(bill.getGrandTotal()).isEqualByComparingTo("30.00");
        assertThat(part.getStockLevel()).isEqualTo(1);
    }

    @Test
    @Transactional
    void gstBillRequiresValidGstin() {
        var part = createPart("Brake Pad", "BP-001", 5, 500, 800);

        assertThatThrownBy(() -> inventory.createBill(new Dtos.BillRequest(
                "GST Customer",
                "BADGST",
                "Kopargaon",
                "",
                InvoiceType.GST,
                LocalDate.now(),
                SupplyType.INTRA_STATE,
                "CASH",
                "",
                List.of(new Dtos.BillItemRequest(part.getId(), 1, BigDecimal.ZERO))
        ))).hasMessageContaining("valid 15-character GSTIN");
    }

    @Test
    @Transactional
    void dealerPurchaseIncreasesStockAndUpdatesPurchasePrice() {
        Supplier supplier = new Supplier();
        supplier.setName("Local Distributor");
        supplier.setDefaultDiscount(BigDecimal.ZERO);
        suppliers.save(supplier);
        var part = createPart("Spark Plug", "SP-001", 4, 100, 150);

        var purchase = inventory.createPurchase(new Dtos.PurchaseRequest(
                supplier.getId(),
                "TEST-DEALER-001",
                LocalDate.now(),
                "Test purchase",
                List.of(new Dtos.PurchaseItemRequest(part.getId(), 4, BigDecimal.valueOf(90), BigDecimal.valueOf(10)))
        ));

        assertThat(purchase.getGrandTotal()).isEqualByComparingTo("600.00");
        assertThat(part.getStockLevel()).isEqualTo(8);
        assertThat(part.getCostPrice()).isEqualByComparingTo("90.00");
    }

    private com.mahalaxmi.autoparts.domain.Part createPart(String name, String number, int stock, double cost, double sale) {
        return inventory.createPart(new Dtos.PartRequest(
                null,
                name,
                number,
                null,
                null,
                null,
                "Universal",
                stock,
                "Main Warehouse",
                null,
                null,
                null,
                null,
                BigDecimal.valueOf(cost),
                BigDecimal.valueOf(sale),
                BigDecimal.ZERO,
                BigDecimal.valueOf(18),
                List.of()
        ));
    }
}
