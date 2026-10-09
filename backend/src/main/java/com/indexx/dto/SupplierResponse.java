package com.indexx.dto;
import java.time.Instant;
public record SupplierResponse(Long id,String name,String email,String phone,boolean active,Instant createdAt,Instant updatedAt) {}
