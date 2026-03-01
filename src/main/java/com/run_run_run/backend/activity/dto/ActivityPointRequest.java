package com.run_run_run.backend.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ActivityPointRequest(
        @Min(-90) @Max(90) double latitude,
        @Min(-180) @Max(180) double longitude,
        @NotNull LocalDateTime recordedAt,
        int sequenceNo
) {
}
