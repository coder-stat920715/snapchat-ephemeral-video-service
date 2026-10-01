package com.example.ephemeral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayTokenRequest(@NotBlank @Size(max = 64) String userId) {
}
