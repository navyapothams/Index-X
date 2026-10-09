package com.indexx.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record WarehouseRequest(@NotBlank @Size(max=30) String code,@NotBlank @Size(max=120) String name,Boolean active) {}
