package com.indexx.controller;

import com.indexx.dto.CategoryRequest;
import com.indexx.dto.CategoryResponse;
import com.indexx.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CatalogService catalog;
    public CategoryController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping public List<CategoryResponse> list() { return catalog.listCategories(); }
    @GetMapping("/{id}") public CategoryResponse get(@PathVariable Long id) { return catalog.getCategory(id); }
    @PostMapping public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createCategory(request));
    }
    @PutMapping("/{id}") public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalog.updateCategory(id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
