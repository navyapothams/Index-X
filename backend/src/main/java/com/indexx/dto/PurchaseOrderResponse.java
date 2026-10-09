package com.indexx.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record PurchaseOrderResponse(Long id,String orderNumber,Long supplierId,String supplierName,
        Long warehouseId,String warehouseName,String status,Instant orderDate,LocalDate expectedDate,
        Instant receivedAt,String notes,BigDecimal total,List<PurchaseItemResponse> items) {}
