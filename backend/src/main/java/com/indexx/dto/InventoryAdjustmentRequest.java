package com.indexx.dto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record InventoryAdjustmentRequest(@NotNull Long productId,@NotNull Long warehouseId,
        @NotNull Integer quantityDelta,@NotBlank String note) {
    @AssertTrue(message="quantityDelta cannot be zero")
    public boolean isNonZero(){return quantityDelta == null || quantityDelta != 0;}
}
