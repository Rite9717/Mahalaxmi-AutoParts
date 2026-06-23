package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.repository.CarBrandRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CarBrandRepository brands;
    private final CarModelRepository models;
    private final PartRepository parts;

    public CatalogController(CarBrandRepository brands, CarModelRepository models, PartRepository parts) {
        this.brands = brands;
        this.models = models;
        this.parts = parts;
    }

    @GetMapping("/brands")
    @Transactional(readOnly = true)
    public List<Dtos.BrandResponse> brands() {
        return brands.findAllByOrderByNameAsc().stream().map(ApiMapper::brand).toList();
    }

    @GetMapping("/brands/{brandId}/models")
    @Transactional(readOnly = true)
    public List<Dtos.ModelResponse> modelsForBrand(@PathVariable long brandId) {
        var brand = brands.findById(brandId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Car brand not found"));
        return models.findByBrandOrderByNameAsc(brand).stream().map(ApiMapper::model).toList();
    }

    @PostMapping("/models")
    @Transactional
    public Dtos.ModelResponse createModel(@Valid @RequestBody Dtos.ModelRequest request) {
        String brandName = request.brandName().trim();
        String modelName = request.modelName().trim().toUpperCase();
        String series = request.series() == null || request.series().trim().isBlank() ? "STANDARD" : request.series().trim().toUpperCase();
        var brand = brands.findByName(brandName).orElseGet(() -> {
            var created = new com.mahalaxmi.autoparts.domain.CarBrand();
            created.setName(brandName);
            return brands.save(created);
        });
        var model = models.findByBrandAndNameAndSeries(brand, modelName, series).orElseGet(() -> {
            var created = new com.mahalaxmi.autoparts.domain.CarModel();
            created.setBrand(brand);
            created.setName(modelName);
            created.setSeries(series);
            created.setYearFrom(request.yearFrom());
            created.setYearTo(request.yearTo());
            return models.save(created);
        });
        return ApiMapper.model(model);
    }

    @GetMapping("/models/{modelId}/parts")
    @Transactional(readOnly = true)
    public List<Dtos.PartResponse> partsForModel(@PathVariable long modelId) {
        if (!models.existsById(modelId)) {
            throw new ResponseStatusException(NOT_FOUND, "Car model not found");
        }
        return parts.findCompatibleParts(modelId).stream().map(ApiMapper::part).toList();
    }
}
