package com.indexx.dto;

import java.time.Instant;

public record BrandResponse(Long id, String name, Instant createdAt, Instant updatedAt) {}
