package com.example.ephemeral.dto;

import com.example.ephemeral.domain.RetentionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UploadRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Min(1) @Max(600) Integer durationSeconds,
        @NotNull RetentionType retentionType) {
}
