package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.service.InventoryImportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inventory")
public class InventoryImportController {
    private final InventoryImportService imports;
    private final PartRepository parts;

    public InventoryImportController(InventoryImportService imports, PartRepository parts) {
        this.imports = imports;
        this.parts = parts;
    }

    @PostMapping("/upload-pdf")
    public List<Dtos.InventoryImportRow> uploadPdf(@RequestPart("file") MultipartFile file) {
        return imports.preview(file);
    }

    @PostMapping("/preview")
    public List<Dtos.InventoryImportRow> preview(@RequestPart("file") MultipartFile file) {
        return imports.preview(file);
    }

    @PostMapping("/save-import")
    public List<Dtos.PartResponse> saveImport(@Valid @RequestBody Dtos.InventoryImportSaveRequest request) {
        return imports.saveImport(request);
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public List<Dtos.PartResponse> search(@RequestParam(name = "q", required = false) String query) {
        String normalized = query == null || query.trim().isBlank() ? null : query.trim();
        return parts.search(normalized, null).stream().map(ApiMapper::part).toList();
    }
}
