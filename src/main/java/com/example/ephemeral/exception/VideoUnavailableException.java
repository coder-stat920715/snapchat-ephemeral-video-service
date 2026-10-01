package com.example.ephemeral.exception;

import org.springframework.http.HttpStatus;

public class VideoUnavailableException extends ApiException {

    public VideoUnavailableException(String message) {
        super(HttpStatus.GONE, message);
    }
}
