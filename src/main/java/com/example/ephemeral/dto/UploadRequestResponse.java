package com.example.ephemeral.dto;

import java.time.Instant;
import java.util.UUID;

public record UploadRequestResponse(UUID videoId, String presignedUploadUrl, Instant expiresAt) {
}
