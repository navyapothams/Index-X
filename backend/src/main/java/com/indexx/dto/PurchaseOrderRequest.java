package com.indexx.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
public record PurchaseOrderRequest(@NotNull @Positive Long supplierId,@NotNull @Positive Long warehouseId,
        LocalDate expectedDate,@Size(max=5000) String notes,@NotEmpty List<@Valid PurchaseItemRequest> items) {}
