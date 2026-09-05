package com.example.financialemail.api;

public class InvalidEmailFileException extends RuntimeException {
    public InvalidEmailFileException(String message) {
        super(message);
    }

    public InvalidEmailFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
