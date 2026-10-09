package com.indexx.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record PurchaseReceiptRequest(@NotEmpty List<@Valid PurchaseReceiptLine> items) {}
