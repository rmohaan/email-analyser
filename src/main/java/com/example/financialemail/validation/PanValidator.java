package com.example.financialemail.validation;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class PanValidator {
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    public Optional<String> normalizeValidPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return Optional.empty();
        }
        String normalized = pan.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return PAN_PATTERN.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }
}
