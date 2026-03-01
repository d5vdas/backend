package com.run_run_run.backend.auth.dto;

public record AuthResponse(
        String token,
        String tokenType
) {
}
