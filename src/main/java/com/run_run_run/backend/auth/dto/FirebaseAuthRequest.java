package com.run_run_run.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record FirebaseAuthRequest(
        @NotBlank String idToken,
        String name
) {
}
