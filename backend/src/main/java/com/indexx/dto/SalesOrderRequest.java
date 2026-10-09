package com.indexx.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
public record SalesOrderRequest(@Positive Long customerId,@NotNull @Positive Long warehouseId,
        @Size(max=5000) String notes,@NotEmpty List<@Valid SalesItemRequest> items) {}
