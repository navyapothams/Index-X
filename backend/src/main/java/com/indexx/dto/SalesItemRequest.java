package com.indexx.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
public record SalesItemRequest(@NotNull @Positive Long productId,@NotNull @Positive Integer quantity,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal discountRate) {}
