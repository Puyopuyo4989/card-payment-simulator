package com.example.cardpayment.cardapplication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /** 許されるのは「申込→審査中」「審査中→承認」「審査中→否決」の3通りだけ（data-model.md 3）。 */
    @ParameterizedTest(name = "{0} → {1} は {2}")
    @CsvSource({
        "APPLIED,      APPLIED,      false",
        "APPLIED,      UNDER_REVIEW, true",
        "APPLIED,      APPROVED,     false",
        "APPLIED,      REJECTED,     false",
        "UNDER_REVIEW, APPLIED,      false",
        "UNDER_REVIEW, UNDER_REVIEW, false",
        "UNDER_REVIEW, APPROVED,     true",
        "UNDER_REVIEW, REJECTED,     true",
        "APPROVED,     APPLIED,      false",
        "APPROVED,     UNDER_REVIEW, false",
        "APPROVED,     APPROVED,     false",
        "APPROVED,     REJECTED,     false",
        "REJECTED,     APPLIED,      false",
        "REJECTED,     UNDER_REVIEW, false",
        "REJECTED,     APPROVED,     false",
        "REJECTED,     REJECTED,     false",
    })
    void 次に進める状態(ApplicationStatus from, ApplicationStatus to, boolean allowed) {
        assertThat(from.canChangeTo(to)).isEqualTo(allowed);
    }

    @Test
    void 承認と否決だけが最終の状態() {
        assertThat(ApplicationStatus.APPLIED.isFinal()).isFalse();
        assertThat(ApplicationStatus.UNDER_REVIEW.isFinal()).isFalse();
        assertThat(ApplicationStatus.APPROVED.isFinal()).isTrue();
        assertThat(ApplicationStatus.REJECTED.isFinal()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "applied", "UNKNOWN", " APPLIED"})
    void 状態の値でない文字列は空を返す(String value) {
        assertThat(ApplicationStatus.parse(value)).isEmpty();
    }
}
