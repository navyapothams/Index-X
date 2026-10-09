package com.indexx.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PurchaseItemRequest(@NotNull @Positive Long productId,@NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal unitCost) {}
