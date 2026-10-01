package com.example.ephemeral.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class VideoNotFoundException extends ApiException {

    public VideoNotFoundException(UUID videoId) {
        super(HttpStatus.NOT_FOUND, "Video not found: " + videoId);
    }
}
