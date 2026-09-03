package com.example.financialemail.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanValidatorTest {
    private final PanValidator validator = new PanValidator();

    @Test
    void normalizesValidPan() {
        assertThat(validator.normalizeValidPan(" abcde1234f ")).contains("ABCDE1234F");
    }

    @Test
    void rejectsMalformedPan() {
        assertThat(validator.normalizeValidPan("ABCDE12345")).isEmpty();
    }
}
