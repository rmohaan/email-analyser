package com.example.financialemail.workflow;

public class DownstreamApiCallException extends RuntimeException {
    private final boolean retryable;

    public DownstreamApiCallException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
