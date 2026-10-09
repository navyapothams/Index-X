package com.indexx.controller;

import com.indexx.dto.SubcategoryRequest;
import com.indexx.dto.SubcategoryResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/subcategories")
public class SubcategoryController {
    private final CatalogService catalog;
    public SubcategoryController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping public List<SubcategoryResponse> list(@RequestParam(required = false) Long categoryId) {
        return catalog.listSubcategories(categoryId);
    }
    @PostMapping public ResponseEntity<SubcategoryResponse> create(@Valid @RequestBody SubcategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createSubcategory(request));
    }
    @PutMapping("/{id}") public SubcategoryResponse update(@PathVariable Long id, @Valid @RequestBody SubcategoryRequest request) {
        return catalog.updateSubcategory(id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.deleteSubcategory(id);
        return ResponseEntity.noContent().build();
    }
}
