package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.Bill;
import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.domain.InvoiceType;
import com.mahalaxmi.autoparts.domain.SupplyType;
import com.mahalaxmi.autoparts.repository.BillRepository;
import com.mahalaxmi.autoparts.service.InventoryService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/bills")
public class BillingController {
    private static final DateTimeFormatter BILL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final BillRepository bills;
    private final InventoryService inventory;
    private final String adminPassword;

    public BillingController(BillRepository bills, InventoryService inventory, @Value("${app.admin.password:1234}") String adminPassword) {
        this.bills = bills;
        this.inventory = inventory;
        this.adminPassword = adminPassword;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Dtos.BillResponse> bills() {
        return bills.findTop100ByBillTypeOrderByCreatedAtDesc(BillType.FINAL).stream().map(ApiMapper::bill).toList();
    }

    @GetMapping("/ongoing")
    @Transactional(readOnly = true)
    public List<Dtos.BillResponse> ongoingBills() {
        return bills.findTop100ByBillTypeAndStatusNotOrderByCreatedAtDesc(BillType.ONGOING, BillStatus.CANCELLED).stream().map(ApiMapper::bill).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Dtos.BillResponse bill(@PathVariable long id) {
        return ApiMapper.bill(findBill(id));
    }

    @PostMapping
    @Transactional
    public Dtos.BillResponse createBill(@Valid @RequestBody Dtos.BillRequest request) {
        return ApiMapper.bill(inventory.createBill(request));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public Dtos.BillResponse cancelBill(@PathVariable long id) {
        return ApiMapper.bill(inventory.cancelBill(id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void deleteCancelledBill(@PathVariable long id, @RequestHeader(name = "X-Admin-Password", required = false) String password) {
        if (adminPassword == null || adminPassword.isBlank() || password == null || !adminPassword.equals(password)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Invalid admin password");
        }
        inventory.deleteCancelledBill(id);
    }

    @PutMapping("/{id}/items")
    @Transactional
    public Dtos.BillResponse updateOngoingBillItems(@PathVariable long id, @Valid @RequestBody Dtos.BillItemsUpdateRequest request) {
        return ApiMapper.bill(inventory.updateOngoingBillItems(id, request));
    }

    @PostMapping("/{id}/payments")
    @Transactional
    public Dtos.BillResponse recordPayment(@PathVariable long id, @Valid @RequestBody Dtos.PaymentRequest request) {
        return ApiMapper.bill(inventory.recordPayment(id, request));
    }

    @PostMapping("/{id}/finalize")
    @Transactional
    public Dtos.BillResponse finalizeBill(@PathVariable long id) {
        return ApiMapper.bill(inventory.finalizeOngoingBill(id));
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public String printBill(@PathVariable long id) {
        Bill bill = bills.findByIdWithItemsAndParts(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
        boolean normalBill = bill.getInvoiceType() == InvoiceType.NORMAL;
        boolean intraState = !normalBill && bill.getSupplyType() == SupplyType.INTRA_STATE;
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < bill.getItems().size(); i++) {
            var item = bill.getItems().get(i);
            var linkedPart = item.getPart();
            String companyName = firstPresent(item.getCompanyName(), linkedPart == null ? null : linkedPart.getCompanyName());
            String partNumber = firstPresent(item.getPartNumber(), linkedPart == null ? null : linkedPart.getPartNumber());
            String serialNo = firstPresent(item.getSerialNo(), linkedPart == null ? null : linkedPart.getSerialNo());
            String hsnCode = firstPresent(item.getHsnCode(), linkedPart == null ? null : linkedPart.getHsnCode());
            String companyLine = blankLine(companyName);
            String partNumberLine = partNumber.isBlank() ? "" : "<span>Part No: " + html(partNumber) + "</span>";
            String serialNoLine = serialNo.isBlank() ? "" : "<span>Serial No: " + html(serialNo) + "</span>";
            if (normalBill) {
                rows.append("""
                    <tr>
                      <td class="center">%d</td>
                      <td><strong>%s</strong>%s</td>
                      <td class="center">%s</td>
                      <td class="right">%d</td>
                      <td class="right">%s</td>
                      <td class="right strong money">%s</td>
                    </tr>
                    """.formatted(
                        i + 1,
                        html(item.getPartName()),
                        companyLine,
                        html(hsnCode.isBlank() ? "-" : hsnCode),
                        item.getQuantity(),
                        rupee(item.getUnitPrice()),
                        rupee(item.getLineTotal())
                ));
            } else {
                BigDecimal cgst = intraState ? half(item.getGstAmount()) : BigDecimal.ZERO;
                BigDecimal sgst = intraState ? half(item.getGstAmount()) : BigDecimal.ZERO;
                BigDecimal igst = intraState ? BigDecimal.ZERO : item.getGstAmount();
                if (intraState) {
                    rows.append("""
                    <tr>
                      <td class="center">%d</td>
                      <td>
                        <strong>%s</strong>
                        %s
                        %s
                        %s
                      </td>
                      <td class="center">%s</td>
                      <td class="right">%d</td>
                      <td class="right money">%s</td>
                      <td class="right money">%s</td>
                      <td class="right money">%s</td>
                      <td class="right money">%s</td>
                      <td class="right strong money">%s</td>
                    </tr>
                    """.formatted(
                            i + 1,
                            html(item.getPartName()),
                            companyLine,
                            partNumberLine,
                            serialNoLine,
                            html(hsnCode.isBlank() ? "-" : hsnCode),
                            item.getQuantity(),
                            rupee(item.getUnitPrice()),
                            rupee(item.getTaxableValue()),
                            rupee(cgst),
                            rupee(sgst),
                            rupee(item.getLineTotal())
                    ));
                } else {
                    rows.append("""
                    <tr>
                      <td class="center">%d</td>
                      <td>
                        <strong>%s</strong>
                        %s
                        %s
                        %s
                      </td>
                      <td class="center">%s</td>
                      <td class="right">%d</td>
                      <td class="right">%s</td>
                      <td class="right">%s</td>
                      <td class="right">%s</td>
                      <td class="right money">%s</td>
                    </tr>
                    """.formatted(
                            i + 1,
                            html(item.getPartName()),
                            companyLine,
                            partNumberLine,
                            serialNoLine,
                            html(hsnCode.isBlank() ? "-" : hsnCode),
                            item.getQuantity(),
                            rupee(item.getUnitPrice()),
                            rupee(item.getTaxableValue()),
                            rupee(igst),
                            rupee(item.getLineTotal())
                    ));
                }
            }
        }

        String notes = nullToBlank(bill.getNotes());
        String invoiceTitle = normalBill ? "Cash Memo" : "Tax Invoice";
        String printLabel = normalBill ? "Print Normal Bill" : "Print GST Bill";
        String supplyType = normalBill ? "Normal bill" : supplyTypeLabel(bill.getSupplyType());
        String customerGstinLine = normalBill ? "" : "<div class=\"muted\">GSTIN: " + html(bill.getCustomerGstin()) + "</div>";
        String customerAddress = nullToBlank(bill.getCustomerAddress()).isBlank() ? "Address not provided" : bill.getCustomerAddress();
        String customerMobile = nullToBlank(bill.getCustomerMobile()).isBlank() ? "Mobile not provided" : "Mobile: " + bill.getCustomerMobile();
        String customerCarLine = nullToBlank(bill.getCarNumber()).isBlank() ? "" : "<div class=\"muted\">Car No.: " + html(bill.getCarNumber()) + "</div>";
        String customerAadhaarLine = nullToBlank(bill.getAadhaarNumber()).isBlank() ? "" : "<div class=\"muted\">Aadhaar: " + html(bill.getAadhaarNumber()) + "</div>";
        String tableClass = normalBill ? "invoice-table normal" : intraState ? "invoice-table gst intra" : "invoice-table gst inter";
        String paymentSummaryTop = bill.getBillType() == BillType.ONGOING ? """
                        <div class="meta-row"><span>Total</span><strong>%s</strong></div>
                        <div class="meta-row"><span>Paid</span><strong>%s</strong></div>
                        <div class="meta-row"><span>Balance</span><strong>%s</strong></div>
                """.formatted(
                rupee(bill.getGrandTotal()),
                rupee(bill.getAmountPaid()),
                rupee(bill.getBalanceAmount())
        ) : "";
        String tableHead = normalBill ? """
                        <tr>
                          <th>S.no</th>
                          <th>Item Description</th>
                          <th>HSN/SAC</th>
                          <th class="right">Qty</th>
                          <th class="right">Rate</th>
                          <th class="right">Amount</th>
                        </tr>
                """ : intraState ? """
                        <tr>
                          <th>S.no</th>
                          <th>Item Description</th>
                          <th>HSN/SAC</th>
                          <th class="right">Qty</th>
                          <th class="right">Rate</th>
                          <th class="right">Taxable</th>
                          <th class="right">CGST 9%%</th>
                          <th class="right">SGST 9%%</th>
                          <th class="right">Amount</th>
                        </tr>
                """ : """
                        <tr>
                          <th>S.no</th>
                          <th>Item Description</th>
                          <th>HSN/SAC</th>
                          <th class="right">Qty</th>
                          <th class="right">Rate</th>
                          <th class="right">Taxable</th>
                          <th class="right">IGST</th>
                          <th class="right">Amount</th>
                        </tr>
                """;
        String totalsRows = normalBill ? """
                        <div class="total-row grand"><span>Total Amount</span><strong>%s</strong></div>
                """.formatted(rupee(bill.getGrandTotal())) : intraState ? """
                        <div class="total-row"><span>Taxable Value</span><strong>%s</strong></div>
                        <div class="total-row"><span>CGST 9%%</span><strong>%s</strong></div>
                        <div class="total-row"><span>SGST 9%%</span><strong>%s</strong></div>
                        <div class="total-row"><span>Total GST</span><strong>%s</strong></div>
                        <div class="total-row grand"><span>Grand Total</span><strong>%s</strong></div>
                """.formatted(
                rupee(bill.getSubtotal()),
                rupee(bill.getCgst()),
                rupee(bill.getSgst()),
                rupee(bill.getGstTotal()),
                rupee(bill.getGrandTotal())
        ) : """
                        <div class="total-row"><span>Taxable Value</span><strong>%s</strong></div>
                        <div class="total-row"><span>IGST</span><strong>%s</strong></div>
                        <div class="total-row"><span>Total GST</span><strong>%s</strong></div>
                        <div class="total-row grand"><span>Grand Total</span><strong>%s</strong></div>
                """.formatted(
                rupee(bill.getSubtotal()),
                rupee(bill.getIgst()),
                rupee(bill.getGstTotal()),
                rupee(bill.getGrandTotal())
        );
        boolean showPaymentSummary = bill.getBillType() == BillType.ONGOING
                || nullToZero(bill.getAmountPaid()).compareTo(BigDecimal.ZERO) > 0
                || nullToZero(bill.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0;
        String paymentRows = showPaymentSummary ? """
                        <div class="total-row"><span>Amount Received</span><strong>%s</strong></div>
                        <div class="total-row"><span>Balance Amount</span><strong>%s</strong></div>
                """.formatted(
                rupee(bill.getAmountPaid()),
                rupee(bill.getBalanceAmount())
        ) : "";

        return """
                <!doctype html>
                <html>
                <head>
                  <title>%s</title>
                  <meta charset="utf-8">
                  <style>
                    :root {
                      color-scheme: light;
                      --ink: #111827;
                      --muted: #5b6472;
                      --line: #d8dee8;
                      --soft: #f5f7fb;
                      --brand: #b91c1c;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: #eef1f5;
                      color: var(--ink);
                      font-family: Arial, Helvetica, sans-serif;
                      font-size: 12px;
                    }
                    .page {
                      width: 210mm;
                      min-height: 297mm;
                      margin: 18px auto;
                      background: white;
                      padding: 14mm;
                      box-shadow: 0 20px 55px rgba(15, 23, 42, 0.18);
                    }
                    .print-action {
                      border: 0;
                      border-radius: 4px;
                      background: var(--brand);
                      color: white;
                      cursor: pointer;
                      font-size: 13px;
                      font-weight: 700;
                      margin-bottom: 14px;
                      padding: 9px 14px;
                    }
                    .invoice-title {
                      border: 2px solid var(--ink);
                      font-size: 17px;
                      font-weight: 800;
                      letter-spacing: 0.08em;
                      padding: 8px 12px;
                      text-align: center;
                      text-transform: uppercase;
                    }
                    .top-grid {
                      display: grid;
                      grid-template-columns: 1.35fr 0.85fr;
                      gap: 16px;
                      margin-top: 14px;
                    }
                    .seller h1 {
                      font-size: 25px;
                      letter-spacing: 0;
                      margin: 0 0 6px;
                      text-transform: uppercase;
                    }
                    .seller .tagline {
                      color: var(--brand);
                      font-size: 11px;
                      font-weight: 800;
                      letter-spacing: 0.08em;
                      margin-bottom: 8px;
                      text-transform: uppercase;
                    }
                    .muted { color: var(--muted); }
                    .box {
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      overflow: hidden;
                    }
                    .box-title {
                      background: var(--soft);
                      border-bottom: 1px solid var(--line);
                      font-size: 11px;
                      font-weight: 800;
                      letter-spacing: 0.05em;
                      padding: 7px 9px;
                      text-transform: uppercase;
                    }
                    .box-body { padding: 9px; }
                    .meta-row, .total-row {
                      display: flex;
                      justify-content: space-between;
                      gap: 14px;
                      padding: 4px 0;
                    }
                    .meta-row span:first-child, .total-row span:first-child { color: var(--muted); }
                    .billing-grid {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 12px;
                      margin-top: 16px;
                    }
                    table.invoice-table {
                      border-collapse: collapse;
                      margin-top: 16px;
                      width: 100%%;
                      table-layout: fixed;
                    }
                    table.invoice-table th {
                      background: #f3f4f6;
                      color: var(--ink);
                      font-size: 10px;
                      letter-spacing: 0.04em;
                      text-transform: uppercase;
                    }
                    table.invoice-table th,
                    table.invoice-table td {
                      border: 1px solid var(--line);
                      padding: 4px 3px;
                      vertical-align: top;
                      word-break: break-word;
                      overflow-wrap: anywhere;
                      font-size: 11px;
                    }
                    table.invoice-table th.right,
                    table.invoice-table td.right,
                    table.invoice-table th.center,
                    table.invoice-table td.center {
                      white-space: nowrap;
                    }
                    table.invoice-table.normal th:nth-child(1),
                    table.invoice-table.normal td:nth-child(1) { width: 7%%; }
                    table.invoice-table.normal th:nth-child(2),
                    table.invoice-table.normal td:nth-child(2) { width: 45%%; }
                    table.invoice-table.normal th:nth-child(3),
                    table.invoice-table.normal td:nth-child(3) { width: 11%%; }
                    table.invoice-table.normal th:nth-child(4),
                    table.invoice-table.normal td:nth-child(4) { width: 8%%; }
                    table.invoice-table.normal th:nth-child(5),
                    table.invoice-table.normal td:nth-child(5) { width: 14%%; }
                    table.invoice-table.normal th:nth-child(6),
                    table.invoice-table.normal td:nth-child(6) { width: 15%%; }
                    table.invoice-table.gst.intra th:nth-child(1),
                    table.invoice-table.gst.intra td:nth-child(1) { width: 5%%; }
                    table.invoice-table.gst.intra th:nth-child(2),
                    table.invoice-table.gst.intra td:nth-child(2) { width: 26%%; }
                    table.invoice-table.gst.intra th:nth-child(3),
                    table.invoice-table.gst.intra td:nth-child(3) { width: 9%%; }
                    table.invoice-table.gst.intra th:nth-child(4),
                    table.invoice-table.gst.intra td:nth-child(4) { width: 6%%; }
                    table.invoice-table.gst.intra th:nth-child(5),
                    table.invoice-table.gst.intra td:nth-child(5) { width: 9%%; }
                    table.invoice-table.gst.intra th:nth-child(6),
                    table.invoice-table.gst.intra td:nth-child(6) { width: 11%%; }
                    table.invoice-table.gst.intra th:nth-child(7),
                    table.invoice-table.gst.intra td:nth-child(7) { width: 10%%; }
                    table.invoice-table.gst.intra th:nth-child(8),
                    table.invoice-table.gst.intra td:nth-child(8) { width: 10%%; }
                    table.invoice-table.gst.intra th:nth-child(9),
                    table.invoice-table.gst.intra td:nth-child(9) { width: 14%%; }
                    table.invoice-table.gst.inter th:nth-child(1),
                    table.invoice-table.gst.inter td:nth-child(1) { width: 5%%; }
                    table.invoice-table.gst.inter th:nth-child(2),
                    table.invoice-table.gst.inter td:nth-child(2) { width: 32%%; }
                    table.invoice-table.gst.inter th:nth-child(3),
                    table.invoice-table.gst.inter td:nth-child(3) { width: 10%%; }
                    table.invoice-table.gst.inter th:nth-child(4),
                    table.invoice-table.gst.inter td:nth-child(4) { width: 7%%; }
                    table.invoice-table.gst.inter th:nth-child(5),
                    table.invoice-table.gst.inter td:nth-child(5) { width: 10%%; }
                    table.invoice-table.gst.inter th:nth-child(6),
                    table.invoice-table.gst.inter td:nth-child(6) { width: 12%%; }
                    table.invoice-table.gst.inter th:nth-child(7),
                    table.invoice-table.gst.inter td:nth-child(7) { width: 10%%; }
                    table.invoice-table.gst.inter th:nth-child(8),
                    table.invoice-table.gst.inter td:nth-child(8) { width: 14%%; }
                    .money {
                      white-space: nowrap;
                      font-variant-numeric: tabular-nums;
                    }
                    td span {
                      color: var(--muted);
                      display: block;
                      font-size: 10px;
                      margin-top: 3px;
                    }
                    .right { text-align: right; }
                    .center { text-align: center; }
                    .strong { font-weight: 800; }
                    .summary-grid {
                      display: grid;
                      grid-template-columns: 1fr 88mm;
                      gap: 16px;
                      margin-top: 16px;
                    }
                    .totals {
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      overflow: hidden;
                    }
                    .total-row {
                      border-bottom: 1px solid var(--line);
                      padding: 8px 10px;
                    }
                    .total-row:last-child { border-bottom: 0; }
                    .grand {
                      background: #f3f4f6;
                      color: var(--ink);
                      font-size: 16px;
                      font-weight: 900;
                      border-top: 2px solid var(--ink);
                    }
                    .terms {
                      color: var(--muted);
                      font-size: 11px;
                      line-height: 1.55;
                    }
                    .footer-grid {
                      display: grid;
                      grid-template-columns: 1fr 70mm;
                      gap: 18px;
                      margin-top: 28px;
                    }
                    .signature {
                      border-top: 1px solid var(--ink);
                      margin-top: 42px;
                      padding-top: 7px;
                      text-align: center;
                    }
                    .status {
                      border-radius: 999px;
                      display: inline-block;
                      font-size: 10px;
                      font-weight: 900;
                      padding: 4px 9px;
                      text-transform: uppercase;
                    }
                    .status-paid { background: #dcfce7; color: #166534; }
                    .status-cancelled { background: #fee2e2; color: #991b1b; }
                    @page { size: A4; margin: 10mm; }
                    @media print {
                      body { background: white; }
                      .page {
                        box-shadow: none;
                        margin: 0;
                        min-height: auto;
                        padding: 0;
                        width: auto;
                      }
                      .print-action { display: none; }
                    }
                  </style>
                </head>
                <body>
                  <main class="page">
                    <button class="print-action" onclick="window.print()">%s</button>
                    <div class="invoice-title">%s</div>

                    <section class="top-grid">
                      <div class="seller">
                        <h1>Mahalaxmi Automobiles</h1>
                        <div class="tagline">Automobile Spare Parts, Retail and Dealer Supply</div>
                        <div>Vedika Shopeez, Shop No. 16, Kopargaon</div>
                        <div class="muted">Phone: 9272365353 / 8484868633</div>
                        <div class="muted">GSTIN: 27AJXPY7428G1Z7</div>
                      </div>
                      <div class="box">
                        <div class="box-title">Invoice Details</div>
                        <div class="box-body">
                          <div class="meta-row"><span>Invoice No.</span><strong>%s</strong></div>
                          <div class="meta-row"><span>Invoice Date</span><strong>%s</strong></div>
                          <div class="meta-row"><span>Payment Mode</span><strong>%s</strong></div>
                          <div class="meta-row"><span>Bill Type</span><strong>%s</strong></div>
                          <div class="meta-row"><span>Status</span><strong class="status %s">%s</strong></div>
                          %s
                        </div>
                      </div>
                    </section>

                    <section class="billing-grid">
                      <div class="box">
                        <div class="box-title">Bill To</div>
                        <div class="box-body">
                          <strong>%s</strong>
                          <div class="muted">%s</div>
                          <div class="muted">%s</div>
                          %s
                          %s
                          %s
                        </div>
                      </div>
                      <div class="box">
                        <div class="box-title">Notes</div>
                        <div class="box-body muted">%s</div>
                      </div>
                    </section>

                    <table class="%s">
                      <thead>
                        %s
                      </thead>
                      <tbody>%s</tbody>
                    </table>

                    <section class="summary-grid">
                      <div class="terms">
                        <strong>Terms and Conditions</strong><br>
                        Goods once sold will be accepted for return only as per store policy and with the original invoice.
                        Electrical/electronic parts must be checked before fitting. Warranty, if any, is as provided by the manufacturer.
                      </div>
                      <div class="totals">
                        %s
                        %s
                      </div>
                    </section>

                    <section class="footer-grid">
                      <div class="muted">
                        This is a computer-generated invoice. Please verify vehicle compatibility before fitting parts.
                      </div>
                      <div class="signature">Authorised Signatory</div>
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(
                html(bill.getBillNumber()),
                html(printLabel),
                html(invoiceTitle),
                html(bill.getBillNumber()),
                bill.getBillingDate() == null ? "" : bill.getBillingDate().format(BILL_DATE_FORMAT),
                html(bill.getPaymentMode()),
                html(supplyType),
                statusClass(bill.getStatus().name()),
                html(bill.getStatus().name()),
                paymentSummaryTop,
                html(bill.getCustomerName()),
                html(customerAddress),
                html(customerMobile),
                customerGstinLine,
                customerCarLine,
                customerAadhaarLine,
                html(notes.isBlank() ? "No additional notes." : notes),
                tableClass,
                tableHead,
                rows,
                totalsRows,
                paymentRows
        );
    }

    private Bill findBill(long id) {
        return bills.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bill not found"));
    }

    private static BigDecimal half(BigDecimal value) {
        return nullToZero(value).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static String supplyTypeLabel(SupplyType supplyType) {
        if (supplyType == SupplyType.INTER_STATE) {
            return "Inter-state (IGST)";
        }
        return "Intra-state (CGST + SGST)";
    }

    private static String statusClass(String status) {
        return "status-" + status.toLowerCase();
    }

    private static String rupee(BigDecimal value) {
        return "Rs " + nullToZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String percent(BigDecimal value) {
        return nullToZero(value).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String html(String value) {
        return HtmlUtils.htmlEscape(nullToBlank(value));
    }

    private static String blankLine(String value) {
        return nullToBlank(value).isBlank() ? "" : "<span>" + html(value) + "</span>";
    }

    private static String firstPresent(String primary, String fallback) {
        String value = nullToBlank(primary);
        if (!value.isBlank()) {
            return value;
        }
        return nullToBlank(fallback);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
