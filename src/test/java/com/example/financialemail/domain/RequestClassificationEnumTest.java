package com.example.financialemail.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestClassificationEnumTest {

    @Test
    void unexpectedSubtypeFallsBackToUnknown() {
        assertThat(RequestSubtype.fromValue("PURCHASE_STATUS"))
                .isEqualTo(RequestSubtype.UNKNOWN);
    }

    @Test
    void unexpectedCategoryFallsBackToUnknown() {
        assertThat(RequestCategory.fromValue("TRANSACTION_STATUS"))
                .isEqualTo(RequestCategory.UNKNOWN);
    }
}
