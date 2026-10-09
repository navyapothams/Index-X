package com.indexx.controller;

import com.indexx.dto.BrandRequest;
import com.indexx.dto.BrandResponse;
import com.indexx.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/brands")
public class BrandController {
    private final CatalogService catalog;
    public BrandController(CatalogService catalog) { this.catalog = catalog; }
    @GetMapping public List<BrandResponse> list() { return catalog.listBrands(); }
    @PostMapping public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createBrand(request));
    }
    @PutMapping("/{id}") public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return catalog.updateBrand(id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.deleteBrand(id);
        return ResponseEntity.noContent().build();
    }
}
