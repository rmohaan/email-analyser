package com.example.financialemail.api;

import com.example.financialemail.validation.InvalidExtractionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidEmailFileException.class)
    ResponseEntity<ApiError> handleInvalidEmailFile(InvalidEmailFileException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid email file", List.of(exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handleMissingFile(MissingServletRequestPartException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid email file", List.of("A .eml file is required"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleOversizedFile(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Email file is too large", List.of("Maximum file size is 10 MB"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleRequestValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(InvalidExtractionException.class)
    ResponseEntity<ApiError> handleExtractionValidation(InvalidExtractionException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), exception.details());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return error(HttpStatus.BAD_GATEWAY, "AI analysis could not be completed", List.of(exception.getClass().getSimpleName()));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), message, details));
    }
}
