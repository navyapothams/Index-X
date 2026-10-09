package com.indexx.dto;
import java.time.Instant;
public record HealthResponse(String status, String message, Instant timestamp) {}
