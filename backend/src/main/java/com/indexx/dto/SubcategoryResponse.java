package com.indexx.dto;

import java.time.Instant;

public record SubcategoryResponse(Long id, Long categoryId, String categoryName, String name,
                                  Instant createdAt, Instant updatedAt) {}
