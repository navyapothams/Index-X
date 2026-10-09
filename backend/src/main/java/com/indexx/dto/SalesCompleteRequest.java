package com.indexx.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record SalesCompleteRequest(@NotBlank @Pattern(regexp="CASH|CARD|BANK_TRANSFER|UPI|OTHER") String paymentMethod,
        @Size(max=120) String paymentReference) {}
