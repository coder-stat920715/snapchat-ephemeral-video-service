package com.example.ephemeral.exception;

import org.springframework.http.HttpStatus;

public class InvalidVideoStateException extends ApiException {

    public InvalidVideoStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
