package com.indexx.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record PaymentResponse(Long id,BigDecimal amount,String paymentMethod,String status,
        String paymentReference,Instant paidAt) {}
