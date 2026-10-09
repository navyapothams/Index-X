package com.indexx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 180) String name,
        String description,
        @NotNull @Positive Long categoryId,
        @Positive Long subcategoryId,
        @Positive Long brandId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
        @NotNull @PositiveOrZero Integer reorderLevel,
        @NotNull Boolean active) {}
