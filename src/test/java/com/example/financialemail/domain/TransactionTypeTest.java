package com.example.financialemail.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTypeTest {

    @Test
    void mapsUnexpectedModelValuesToUnknown() {
        assertThat(TransactionType.fromValue("TRANSACTION_STATUS"))
                .isEqualTo(TransactionType.UNKNOWN);
    }

    @Test
    void mapsKnownValuesCaseInsensitively() {
        assertThat(TransactionType.fromValue(" redemption "))
                .isEqualTo(TransactionType.REDEMPTION);
    }
}
