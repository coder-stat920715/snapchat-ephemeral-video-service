package com.example.ephemeral.dto;

public record PlayTokenResponse(String playbackToken, String cloudfrontSignedUrl) {
}
