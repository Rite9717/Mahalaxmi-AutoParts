package com.mahalaxmi.autoparts.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
public class CarModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CarBrand brand;

    @Column(nullable = false)
    private String name;

    private String series;
    private Integer yearFrom;
    private Integer yearTo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @JsonIgnore
    @ManyToMany(mappedBy = "compatibleModels")
    private Set<Part> parts = new LinkedHashSet<>();

    public Long getId() {
        return id;
    }

    public CarBrand getBrand() {
        return brand;
    }

    public void setBrand(CarBrand brand) {
        this.brand = brand;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public Integer getYearFrom() {
        return yearFrom;
    }

    public void setYearFrom(Integer yearFrom) {
        this.yearFrom = yearFrom;
    }

    public Integer getYearTo() {
        return yearTo;
    }

    public void setYearTo(Integer yearTo) {
        this.yearTo = yearTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Part> getParts() {
        return parts;
    }
}
