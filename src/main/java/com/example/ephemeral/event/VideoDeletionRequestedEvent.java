package com.example.ephemeral.event;

import java.util.UUID;

public record VideoDeletionRequestedEvent(UUID videoId, String s3Key, DeletionMode mode) {
}
