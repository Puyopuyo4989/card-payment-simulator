package com.example.cardpayment.cardapplication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 申込の状態（data-model.md 3、spec FR-014）。 */
class ApplicationStatusTest {

    @Test
    void 状態は4つ() {
        assertThat(ApplicationStatus.values())
                .containsExactly(
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.APPROVED,
                        ApplicationStatus.REJECTED);
    }

    @Test
    void 状態の値の文字列をenumに変える() {
        assertThat(ApplicationStatus.parse("APPLIED")).contains(ApplicationStatus.APPLIED);
        assertThat(ApplicationStatus.parse("UNDER_REVIEW"))
                .contains(ApplicationStatus.UNDER_REVIEW);
        assertThat(ApplicationStatus.parse("APPROVED")).contains(ApplicationStatus.APPROVED);
        assertThat(ApplicationStatus.parse("REJECTED")).contains(ApplicationStatus.REJECTED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "applied", "UNKNOWN", " APPLIED"})
    void 状態の値でない文字列は空を返す(String value) {
        assertThat(ApplicationStatus.parse(value)).isEmpty();
    }
}
