package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.DealerOrder;
import com.mahalaxmi.autoparts.domain.DealerOrderItem;
import com.mahalaxmi.autoparts.repository.DealerOrderRepository;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/orders")
public class DealerOrderController {
    private static final DateTimeFormatter ORDER_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");

    private final DealerOrderRepository orders;
    private final String adminPassword;

    public DealerOrderController(DealerOrderRepository orders, @Value("${app.admin.password:1234}") String adminPassword) {
        this.orders = orders;
        this.adminPassword = adminPassword;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Dtos.DealerOrderResponse> orders() {
        return orders.findTop100ByOrderByCreatedAtDesc().stream().map(ApiMapper::dealerOrder).toList();
    }

    @PostMapping
    @Transactional
    public Dtos.DealerOrderResponse create(@Valid @RequestBody Dtos.DealerOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Order must contain at least one item");
        }
        DealerOrder order = new DealerOrder();
        order.setOrderNumber("ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + ORDER_NUMBER_FORMAT.format(java.time.LocalDateTime.now()));
        order.setDealerName(request.dealerName() == null ? null : request.dealerName().trim());
        order.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        order.setNotes(request.notes() == null ? null : request.notes().trim());
        for (Dtos.DealerOrderItemRequest item : request.items()) {
            String itemName = item.itemName() == null ? "" : item.itemName().trim();
            String partNumber = item.partNumber() == null ? "" : item.partNumber().trim().toUpperCase();
            if (itemName.isBlank() && partNumber.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Every order line needs an item name or part number");
            }
            DealerOrderItem line = new DealerOrderItem();
            line.setItemName(itemName.isBlank() ? null : itemName);
            line.setPartNumber(partNumber.isBlank() ? null : partNumber);
            line.setQuantity(item.quantity());
            line.setNote(item.note() == null ? null : item.note().trim());
            order.addItem(line);
        }
        return ApiMapper.dealerOrder(orders.save(order));
    }

    @PutMapping("/{id}")
    @Transactional
    public Dtos.DealerOrderResponse update(@PathVariable long id, @Valid @RequestBody Dtos.DealerOrderUpdateRequest request) {
        DealerOrder order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Order must contain at least one item");
        }
        order.setDealerName(request.dealerName() == null ? null : request.dealerName().trim());
        order.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        order.setNotes(request.notes() == null ? null : request.notes().trim());
        order.getItems().clear();
        for (Dtos.DealerOrderItemRequest item : request.items()) {
            String itemName = item.itemName() == null ? "" : item.itemName().trim();
            String partNumber = item.partNumber() == null ? "" : item.partNumber().trim().toUpperCase();
            if (itemName.isBlank() && partNumber.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Every order line needs an item name or part number");
            }
            DealerOrderItem line = new DealerOrderItem();
            line.setItemName(itemName.isBlank() ? null : itemName);
            line.setPartNumber(partNumber.isBlank() ? null : partNumber);
            line.setQuantity(item.quantity());
            line.setNote(item.note() == null ? null : item.note().trim());
            order.addItem(line);
        }
        return ApiMapper.dealerOrder(orders.save(order));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable long id, @RequestHeader(name = "X-Admin-Password", required = false) String password) {
        if (adminPassword == null || adminPassword.isBlank() || password == null || !adminPassword.equals(password)) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid admin password");
        }
        DealerOrder order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        orders.delete(order);
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public String print(@PathVariable long id) {
        DealerOrder order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < order.getItems().size(); i++) {
            rows.append(row(order.getItems().get(i), i + 1));
        }
        String dealer = order.getDealerName() == null || order.getDealerName().isBlank() ? "Dealer Order List" : order.getDealerName();
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <title>%s</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 0; background: #eceff3; color: #111827; }
                    .page { width: 210mm; min-height: 297mm; margin: 18px auto; background: white; padding: 14mm; box-shadow: 0 20px 55px rgba(15, 23, 42, 0.18); }
                    .print-action { border: 0; border-radius: 4px; background: #b91c1c; color: white; cursor: pointer; font-size: 13px; font-weight: 700; margin-bottom: 14px; padding: 9px 14px; }
                    .title { border: 2px solid #111827; font-size: 18px; font-weight: 800; padding: 8px 12px; text-align: center; text-transform: uppercase; }
                    .meta { display: flex; justify-content: space-between; gap: 12px; margin-top: 14px; }
                    .box { border: 1px solid #d8dee8; border-radius: 6px; padding: 10px; flex: 1; }
                    .box strong { display: block; margin-bottom: 4px; }
                    table { width: 100%%; border-collapse: collapse; margin-top: 16px; }
                    th, td { border: 1px solid #d8dee8; padding: 8px 6px; text-align: left; }
                    th { background: #f3f4f6; text-transform: uppercase; font-size: 11px; }
                    .right { text-align: right; }
                    .muted { color: #5b6472; }
                    .footer { margin-top: 24px; display: flex; justify-content: space-between; }
                    @media print { .page { box-shadow: none; margin: 0; width: auto; min-height: auto; padding: 0; } .print-action { display: none; } body { background: white; } }
                  </style>
                </head>
                <body>
                  <main class="page">
                    <button class="print-action" onclick="window.print()">Print / Save PDF</button>
                    <div class="title">Dealer Order List</div>
                    <div class="meta">
                      <div class="box">
                        <strong>Dealer</strong>
                        <div>%s</div>
                        <div class="muted">%s</div>
                      </div>
                      <div class="box">
                        <strong>Order Details</strong>
                        <div>Order No: %s</div>
                        <div>Order Date: %s</div>
                        <div class="muted">%s</div>
                      </div>
                    </div>
                    <table>
                      <thead>
                        <tr>
                          <th>S.no</th>
                          <th>Item Name</th>
                          <th>Part No.</th>
                          <th class="right">Qty</th>
                          <th>Note</th>
                        </tr>
                      </thead>
                      <tbody>%s</tbody>
                    </table>
                    <div class="footer">
                      <div class="muted">Use this list to place your dealer order.</div>
                      <div><strong>Authorized Signatory</strong></div>
                    </div>
                  </main>
                </body>
                </html>
                """.formatted(
                escape(order.getOrderNumber()),
                escape(dealer),
                escape(order.getNotes() == null || order.getNotes().isBlank() ? "No notes" : order.getNotes()),
                escape(order.getOrderNumber()),
                escape(order.getOrderDate() == null ? "" : order.getOrderDate().toString()),
                escape("Created for dealer ordering"),
                rows.toString()
        );
    }

    private String row(DealerOrderItem item, int index) {
        return """
                <tr>
                  <td>%d</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td class="right">%d</td>
                  <td>%s</td>
                </tr>
                """.formatted(
                index,
                escape(nullToDash(item.getItemName())),
                escape(nullToDash(item.getPartNumber())),
                item.getQuantity(),
                escape(nullToDash(item.getNote()))
        );
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
