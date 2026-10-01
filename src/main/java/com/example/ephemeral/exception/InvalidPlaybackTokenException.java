package com.example.ephemeral.exception;

import org.springframework.http.HttpStatus;

public class InvalidPlaybackTokenException extends ApiException {

    public InvalidPlaybackTokenException() {
        super(HttpStatus.GONE, "Playback token is invalid, expired or already used");
    }
}
