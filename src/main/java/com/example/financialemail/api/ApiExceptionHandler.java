package com.example.financialemail.api;

import com.example.financialemail.validation.InvalidExtractionException;
import com.example.financialemail.service.AiAnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidEmailFileException.class)
    ResponseEntity<ApiError> handleInvalidEmailFile(InvalidEmailFileException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid email file",
                detail(exception.getMessage(), "The email file could not be processed"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handleMissingFile(MissingServletRequestPartException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid email file", List.of("A .eml file is required"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleOversizedFile(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Email file is too large", List.of("Maximum file size is 10 MB"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type",
                List.of("Use multipart/form-data with a file part named 'file'"));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> handleMalformedMultipart(MultipartException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid multipart request",
                List.of("The multipart upload could not be parsed"));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request",
                List.of("The request could not be parsed"));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    ResponseEntity<ApiError> handleResponseSerialization(HttpMessageNotWritableException exception) {
        LOGGER.error("API response serialization failed", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Response could not be generated",
                List.of("The server could not serialize the processing result"));
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

    @ExceptionHandler(AiAnalysisException.class)
    ResponseEntity<ApiError> handleAiAnalysis(AiAnalysisException exception) {
        LOGGER.warn("AI analysis failed", exception);
        return error(HttpStatus.BAD_GATEWAY, "AI analysis could not be completed",
                List.of("The model did not return a valid structured response"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        LOGGER.error("Unexpected email analysis failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Email analysis could not be completed",
                List.of("An unexpected server error occurred"));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), message, details));
    }

    private List<String> detail(String value, String fallback) {
        return List.of(value == null || value.isBlank() ? fallback : value);
    }
}
