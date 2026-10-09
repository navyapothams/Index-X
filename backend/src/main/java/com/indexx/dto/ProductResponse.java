package com.indexx.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id, String sku, String name, String description,
        Long categoryId, String categoryName,
        Long subcategoryId, String subcategoryName,
        Long brandId, String brandName,
        BigDecimal sellingPrice, BigDecimal costPrice, Integer reorderLevel,
        boolean active, Instant createdAt, Instant updatedAt) {}
