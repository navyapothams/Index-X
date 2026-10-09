package com.indexx.dto;
import java.time.Instant;
public record WarehouseResponse(Long id,String code,String name,boolean active,Instant createdAt,Instant updatedAt) {}
