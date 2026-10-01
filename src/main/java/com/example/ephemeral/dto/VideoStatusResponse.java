package com.example.ephemeral.dto;

import com.example.ephemeral.domain.VideoStatus;

import java.time.Instant;
import java.util.UUID;

public record VideoStatusResponse(UUID videoId, VideoStatus status, Instant expiresAt) {
}
