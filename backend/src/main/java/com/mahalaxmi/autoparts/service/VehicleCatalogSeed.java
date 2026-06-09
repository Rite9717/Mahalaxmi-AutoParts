package com.mahalaxmi.autoparts.service;

import com.mahalaxmi.autoparts.domain.CarBrand;
import com.mahalaxmi.autoparts.domain.CarModel;
import com.mahalaxmi.autoparts.repository.CarBrandRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VehicleCatalogSeed implements CommandLineRunner {
    private final CarBrandRepository brands;
    private final CarModelRepository models;

    public VehicleCatalogSeed(CarBrandRepository brands, CarModelRepository models) {
        this.brands = brands;
        this.models = models;
    }

    @Override
    @Transactional
    public void run(String... args) {
        catalog().forEach((brandName, entries) -> {
            CarBrand brand = brands.findByName(brandName).orElseGet(() -> {
                CarBrand created = new CarBrand();
                created.setName(brandName);
                return brands.save(created);
            });
            for (String entry : entries) {
                String[] parts = entry.split("\\|", -1);
                String modelName = parts[0];
                String series = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "Standard";
                models.findByBrandAndNameAndSeries(brand, modelName, series).orElseGet(() -> {
                    CarModel model = new CarModel();
                    model.setBrand(brand);
                    model.setName(modelName);
                    model.setSeries(series);
                    return models.save(model);
                });
            }
        });
    }

    private Map<String, List<String>> catalog() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        catalog.put("Maruti Suzuki", List.of(
                "800|Type 1", "800|Type 2", "Alto|Type 1", "Alto|Type 2", "Alto K10|Type 1", "Alto K10|Type 2",
                "WagonR|Type 1", "WagonR|Type 2", "WagonR|Type 3", "Zen|Type 1", "Zen Estilo|Type 2",
                "Esteem|Type 1", "Gypsy|Type 1", "Omni|Type 1", "Eeco|Type 1", "Swift|Type 1", "Swift|Type 2", "Swift|Type 3",
                "Swift Dzire|Type 1", "Swift Dzire|Type 2", "Dzire|Type 3", "Baleno|Type 1", "Baleno|Type 2",
                "Ritz|Type 1", "Celerio|Type 1", "Celerio|Type 2", "A-Star|Type 1", "S-Presso|Type 1",
                "Ertiga|Type 1", "Ertiga|Type 2", "Brezza|Type 1", "Vitara Brezza|Type 1", "Grand Vitara|Type 1",
                "Ciaz|Type 1", "Ignis|Type 1", "XL6|Type 1", "Fronx|Type 1", "Jimny|Type 1", "Super Carry|Type 1"
        ));
        catalog.put("Hyundai", List.of("Santro|Type 1", "Santro Xing|Type 2", "i10|Type 1", "Grand i10|Type 2", "Grand i10 Nios|Type 3", "i20|Type 1", "Elite i20|Type 2", "i20|Type 3", "Accent|Type 1", "Verna|Type 1", "Verna|Type 2", "Xcent|Type 1", "Aura|Type 1", "Venue|Type 1", "Creta|Type 1", "Creta|Type 2", "Alcazar|Type 1", "Eon|Type 1"));
        catalog.put("Tata", List.of("Indica|Type 1", "Indigo|Type 1", "Nano|Type 1", "Tiago|Type 1", "Tigor|Type 1", "Altroz|Type 1", "Punch|Type 1", "Nexon|Type 1", "Nexon|Type 2", "Harrier|Type 1", "Safari|Type 1", "Sumo|Type 1", "Ace|Type 1"));
        catalog.put("Mahindra", List.of("Bolero|Type 1", "Bolero Neo|Type 2", "Scorpio|Type 1", "Scorpio N|Type 2", "XUV300|Type 1", "XUV500|Type 1", "XUV700|Type 1", "Thar|Type 1", "Thar|Type 2", "KUV100|Type 1", "Marazzo|Type 1", "Jeep|Type 1"));
        catalog.put("Honda", List.of("City|Type 1", "City|Type 2", "Amaze|Type 1", "Amaze|Type 2", "Jazz|Type 1", "WR-V|Type 1", "Brio|Type 1", "Elevate|Type 1"));
        catalog.put("Toyota", List.of("Innova|Type 1", "Innova Crysta|Type 2", "Fortuner|Type 1", "Fortuner|Type 2", "Etios|Type 1", "Etios Liva|Type 1", "Glanza|Type 1", "Urban Cruiser|Type 1", "Hyryder|Type 1"));
        catalog.put("Kia", List.of("Seltos|Type 1", "Sonet|Type 1", "Carens|Type 1"));
        catalog.put("Renault", List.of("Kwid|Type 1", "Duster|Type 1", "Triber|Type 1", "Kiger|Type 1", "Scala|Type 1"));
        catalog.put("Nissan", List.of("Micra|Type 1", "Sunny|Type 1", "Magnite|Type 1", "Terrano|Type 1"));
        catalog.put("Ford", List.of("Figo|Type 1", "Figo Aspire|Type 1", "EcoSport|Type 1", "Fiesta|Type 1", "Endeavour|Type 1"));
        catalog.put("Volkswagen", List.of("Polo|Type 1", "Vento|Type 1", "Ameo|Type 1", "Taigun|Type 1", "Virtus|Type 1"));
        catalog.put("Skoda", List.of("Fabia|Type 1", "Rapid|Type 1", "Kushaq|Type 1", "Slavia|Type 1", "Octavia|Type 1"));
        catalog.put("MG", List.of("Hector|Type 1", "Astor|Type 1", "Gloster|Type 1", "Comet EV|Type 1"));
        catalog.put("Jeep", List.of("Compass|Type 1", "Meridian|Type 1"));
        catalog.put("Force", List.of("Trax|Type 1", "Gurkha|Type 1", "Traveller|Type 1"));
        return catalog;
    }
}
