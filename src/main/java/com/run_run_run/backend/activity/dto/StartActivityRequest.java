package com.run_run_run.backend.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartActivityRequest(
        @NotBlank @Size(max = 30) String type
) {
}
