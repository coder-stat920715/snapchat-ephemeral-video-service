package com.example.ephemeral.dto;

import java.util.UUID;

public record CompleteResponse(UUID videoId, String action) {
}
