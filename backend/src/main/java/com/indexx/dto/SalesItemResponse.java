package com.indexx.dto;
import java.math.BigDecimal;
public record SalesItemResponse(Long id,Long productId,String sku,String productName,Integer quantity,
        BigDecimal unitPrice,BigDecimal discountRate,BigDecimal lineTotal) {}
