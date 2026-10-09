package com.indexx.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record SalesOrderResponse(Long id,String orderNumber,Long customerId,String customerName,
        Long warehouseId,String warehouseName,String status,Instant orderDate,String notes,
        BigDecimal total,List<SalesItemResponse> items,List<PaymentResponse> payments) {}
