package com.mahalaxmi.autoparts.service;

import com.mahalaxmi.autoparts.domain.CarBrand;
import com.mahalaxmi.autoparts.domain.CarModel;
import com.mahalaxmi.autoparts.domain.Part;
import com.mahalaxmi.autoparts.domain.Supplier;
import com.mahalaxmi.autoparts.repository.CarBrandRepository;
import com.mahalaxmi.autoparts.repository.CarModelRepository;
import com.mahalaxmi.autoparts.repository.PartRepository;
import com.mahalaxmi.autoparts.repository.SupplierRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;

public class SeedData implements CommandLineRunner {
    private final CarBrandRepository brands;
    private final CarModelRepository models;
    private final PartRepository parts;
    private final SupplierRepository suppliers;

    public SeedData(CarBrandRepository brands, CarModelRepository models, PartRepository parts, SupplierRepository suppliers) {
        this.brands = brands;
        this.models = models;
        this.parts = parts;
        this.suppliers = suppliers;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (parts.count() > 0) {
            return;
        }

        Supplier local = supplier("Local Distributor", "Mahalaxmi Counter", "9272365353", BigDecimal.valueOf(5));
        Supplier importPdf = supplier("Imported PDF", "Invoice Upload", "8484868633", BigDecimal.ZERO);
        suppliers.saveAll(List.of(local, importPdf));

        CarBrand maruti = brand("Maruti Suzuki");
        CarBrand hyundai = brand("Hyundai");
        CarBrand tata = brand("Tata");
        CarBrand mahindra = brand("Mahindra");
        brands.saveAll(List.of(maruti, hyundai, tata, mahindra));

        CarModel swiftLxi = model(maruti, "Swift", "LXI", 2012, null);
        CarModel swiftVxi = model(maruti, "Swift", "VXI", 2012, null);
        CarModel balenoDelta = model(maruti, "Baleno", "Delta", 2015, null);
        CarModel i20Sportz = model(hyundai, "i20", "Sportz", 2014, null);
        CarModel altrozXm = model(tata, "Altroz", "XM", 2020, null);
        CarModel xuv300W6 = model(mahindra, "XUV300", "W6", 2019, null);
        models.saveAll(List.of(swiftLxi, swiftVxi, balenoDelta, i20Sportz, altrozXm, xuv300W6));

        Part oilFilter = part("Oil Filter", "OF-SW-001", "Filters and belts", "A-01", 35, 180, 280, 18, local.getName());
        Part airFilter = part("Air Filter", "AF-SW-002", "Filters and belts", "A-01", 25, 290, 460, 18, local.getName());
        Part brakePad = part("Brake Pad Set (Front)", "BP-UN-010", "Brake and suspension", "B-02", 18, 1450, 2100, 28, local.getName());
        Part sparkPlug = part("Spark Plug Set", "SP-UN-014", "Electrical and ignition", "C-03", 40, 520, 780, 18, local.getName());
        Part headLamp = part("Head Lamp Assembly", "HL-AL-050", "Electrical and lighting", "C-03", 8, 2400, 3200, 18, importPdf.getName());
        Part clutchCable = part("Clutch Cable", "CC-XUV-118", "Cables and controls", "D-04", 12, 410, 690, 18, local.getName());

        fit(oilFilter, swiftLxi, swiftVxi, balenoDelta, i20Sportz, altrozXm, xuv300W6);
        fit(airFilter, swiftLxi, swiftVxi, balenoDelta, i20Sportz, altrozXm, xuv300W6);
        fit(brakePad, swiftLxi, swiftVxi, balenoDelta, i20Sportz, altrozXm, xuv300W6);
        fit(sparkPlug, swiftLxi, swiftVxi, balenoDelta, i20Sportz, altrozXm);
        fit(headLamp, altrozXm);
        fit(clutchCable, xuv300W6);

        parts.saveAll(List.of(oilFilter, airFilter, brakePad, sparkPlug, headLamp, clutchCable));
    }

    private Supplier supplier(String name, String contact, String phone, BigDecimal discount) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContactPerson(contact);
        supplier.setPhone(phone);
        supplier.setAddress("Kopargaon");
        supplier.setDefaultDiscount(discount);
        return supplier;
    }

    private CarBrand brand(String name) {
        CarBrand brand = new CarBrand();
        brand.setName(name);
        return brand;
    }

    private CarModel model(CarBrand brand, String name, String series, Integer yearFrom, Integer yearTo) {
        CarModel model = new CarModel();
        model.setBrand(brand);
        model.setName(name);
        model.setSeries(series);
        model.setYearFrom(yearFrom);
        model.setYearTo(yearTo);
        return model;
    }

    private Part part(String name, String number, String section, String rack, int stock, double cost, double sale, double gst, String supplier) {
        Part part = new Part();
        part.setName(name);
        part.setPartNumber(number);
        part.setSection(section);
        part.setRackNumber(rack);
        part.setShelfBin(rack + "-BIN");
        part.setWarehouseLocation("Main Warehouse / " + rack);
        part.setCarCompatibility("Assigned through brand/model fitment");
        part.setStockLevel(stock);
        part.setCostPrice(BigDecimal.valueOf(cost));
        part.setSellingPrice(BigDecimal.valueOf(sale));
        part.setGstRate(BigDecimal.valueOf(gst));
        part.setSupplier(supplier);
        return part;
    }

    private void fit(Part part, CarModel... compatibleModels) {
        part.getCompatibleModels().addAll(List.of(compatibleModels));
    }
}
