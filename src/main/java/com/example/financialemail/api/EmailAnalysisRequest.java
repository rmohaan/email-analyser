package com.example.financialemail.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailAnalysisRequest(
        @NotBlank(message = "emailBody is required")
        @Size(max = 200_000, message = "emailBody must not exceed 200,000 characters")
        String emailBody) {
}
