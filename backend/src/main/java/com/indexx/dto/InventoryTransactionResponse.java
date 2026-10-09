package com.indexx.dto;
import java.time.Instant;
public record InventoryTransactionResponse(Long id,Long productId,String sku,String productName,
        Long warehouseId,String warehouseCode,String transactionType,Integer quantityDelta,
        Long purchaseOrderId,Long salesOrderId,Long returnId,Long createdBy,String note,Instant createdAt) {}
