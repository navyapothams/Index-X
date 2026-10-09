package com.indexx.controller;

import com.indexx.dto.ProductRequest;
import com.indexx.dto.ProductResponse;
import com.indexx.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final CatalogService catalog;
    public ProductController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    public Page<ProductResponse> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) Long subcategoryId,
                                     @RequestParam(required = false) Long brandId,
                                     @RequestParam(required = false) BigDecimal minPrice,
                                     @RequestParam(required = false) BigDecimal maxPrice,
                                     @RequestParam(required = false) Boolean active,
                                     @RequestParam(defaultValue = "name") String sort,
                                     @RequestParam(defaultValue = "ASC") String direction,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return catalog.listProducts(q, categoryId, subcategoryId, brandId, minPrice, maxPrice,
                active, sort, direction, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) { return catalog.getProduct(id); }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createProduct(request));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return catalog.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        catalog.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
