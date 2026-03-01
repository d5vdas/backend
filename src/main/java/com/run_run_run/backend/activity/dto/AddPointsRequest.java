package com.run_run_run.backend.activity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddPointsRequest(
        @NotEmpty List<@Valid ActivityPointRequest> points
) {
}
