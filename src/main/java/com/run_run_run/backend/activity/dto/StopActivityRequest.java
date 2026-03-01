package com.run_run_run.backend.activity.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record StopActivityRequest(
        @NotNull LocalDateTime endedAt
) {
}
