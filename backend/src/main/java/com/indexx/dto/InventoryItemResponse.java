package com.indexx.dto;
public record InventoryItemResponse(Long productId,String sku,String productName,Long warehouseId,
        String warehouseCode,String warehouseName,Integer quantityOnHand,Integer reorderLevel,boolean lowStock) {}
