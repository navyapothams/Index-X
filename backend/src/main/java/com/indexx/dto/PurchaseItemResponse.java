package com.indexx.dto;
import java.math.BigDecimal;
public record PurchaseItemResponse(Long id,Long productId,String sku,String productName,
        Integer quantityOrdered,Integer quantityReceived,BigDecimal unitCost,BigDecimal lineTotal) {}
