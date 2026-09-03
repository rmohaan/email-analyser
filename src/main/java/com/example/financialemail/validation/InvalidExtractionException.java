package com.example.financialemail.validation;

import java.util.List;

public class InvalidExtractionException extends RuntimeException {
    private final List<String> details;

    public InvalidExtractionException(List<String> details) {
        super("The AI response did not pass extraction validation");
        this.details = List.copyOf(details);
    }

    public List<String> details() {
        return details;
    }
}
