package com.indexx.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record PurchaseReceiptLine(@NotNull @Positive Long itemId,@NotNull @Positive Integer quantity) {}
