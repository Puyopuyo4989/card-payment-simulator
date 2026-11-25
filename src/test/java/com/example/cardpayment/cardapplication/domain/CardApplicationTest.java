package com.example.cardpayment.cardapplication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.cardpayment.cardapplication.domain.StatusTransitionException.Reason;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** 状態の遷移のルール（data-model.md 3、spec FR-015）。Spring も DB も使わない。値はすべて架空のもの。 */
class CardApplicationTest {

    private static final Instant APPLIED_AT = Instant.parse("2026-11-25T01:30:00Z");
    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00.123456Z");
    private static final String STAFF = "テスト 担当A";
    private static final String REASON = "テスト用の否決理由";

    /** 許される3通り。否決のときは理由を渡す。 */
    @ParameterizedTest(name = "{0} → {1} は許される")
    @CsvSource({
        "APPLIED,      UNDER_REVIEW",
        "UNDER_REVIEW, APPROVED",
        "UNDER_REVIEW, REJECTED",
    })
    void 許される変更は変更後の申込と履歴の1行を返す(ApplicationStatus from, ApplicationStatus to) {
        CardApplication application = applicationIn(from, 3);
        String reason = to == ApplicationStatus.REJECTED ? REASON : null;

        StatusChange change = application.changeStatus(to, STAFF, reason, NOW);

        CardApplication changed = change.application();
        assertThat(changed.status()).isEqualTo(to);
        assertThat(changed.updatedAt()).isEqualTo(NOW);
        // version を増やすのは DB（UPDATE … version = version + 1）
        assertThat(changed.version()).isEqualTo(3);
        assertThat(changed.applicationNumber()).isEqualTo("A00000001");
        assertThat(changed.appliedAt()).isEqualTo(APPLIED_AT);
        assertThat(changed.nameKanji()).isEqualTo("テスト 太郎");

        StatusHistoryEntry history = change.history();
        assertThat(history.changedAt()).isEqualTo(NOW);
        assertThat(history.fromStatus()).isEqualTo(from);
        assertThat(history.toStatus()).isEqualTo(to);
        assertThat(history.staffName()).isEqualTo(STAFF);
        assertThat(history.rejectionReason()).isEqualTo(reason);
    }

    /** 許されない13通りと、その理由（data-model.md 3 の表）。 */
    @ParameterizedTest(name = "{0} → {1} は {2}")
    @CsvSource({
        "APPLIED,      APPROVED,     REVIEW_REQUIRED_FIRST",
        "APPLIED,      REJECTED,     REVIEW_REQUIRED_FIRST",
        "APPROVED,     APPLIED,      ALREADY_DECIDED",
        "APPROVED,     UNDER_REVIEW, ALREADY_DECIDED",
        "APPROVED,     APPROVED,     ALREADY_DECIDED",
        "APPROVED,     REJECTED,     ALREADY_DECIDED",
        "REJECTED,     APPLIED,      ALREADY_DECIDED",
        "REJECTED,     UNDER_REVIEW, ALREADY_DECIDED",
        "REJECTED,     APPROVED,     ALREADY_DECIDED",
        "REJECTED,     REJECTED,     ALREADY_DECIDED",
        "APPLIED,      APPLIED,      NOT_ALLOWED",
        "UNDER_REVIEW, APPLIED,      NOT_ALLOWED",
        "UNDER_REVIEW, UNDER_REVIEW, NOT_ALLOWED",
    })
    void 許されない変更は理由つきの例外(ApplicationStatus from, ApplicationStatus to, Reason expected) {
        CardApplication application = applicationIn(from, 0);

        // 否決の理由を渡しても、遷移の判定は変わらない
        StatusTransitionException exception =
                catchThrowableOfType(
                        StatusTransitionException.class,
                        () -> application.changeStatus(to, STAFF, REASON, NOW));

        assertThat(exception.reason()).isEqualTo(expected);
        // 例外のメッセージに担当者名・理由を入れない（research.md R11-1 の2）
        assertThat(exception.getMessage()).doesNotContain(STAFF, REASON);
    }

    @Test
    void 否決で理由がnullならコードの誤り() {
        CardApplication application = applicationIn(ApplicationStatus.UNDER_REVIEW, 0);

        assertThatThrownBy(() ->
                        application.changeStatus(ApplicationStatus.REJECTED, STAFF, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain(STAFF);
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"UNDER_REVIEW", "APPROVED"})
    void 否決でない変更では理由を履歴に残さない(ApplicationStatus to) {
        ApplicationStatus from =
                to == ApplicationStatus.UNDER_REVIEW
                        ? ApplicationStatus.APPLIED
                        : ApplicationStatus.UNDER_REVIEW;

        StatusChange change = applicationIn(from, 0).changeStatus(to, STAFF, REASON, NOW);

        assertThat(change.history().rejectionReason()).isNull();
    }

    @Test
    void 元の申込は変わらない() {
        CardApplication application = applicationIn(ApplicationStatus.APPLIED, 0);

        application.changeStatus(ApplicationStatus.UNDER_REVIEW, STAFF, null, NOW);

        assertThat(application.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application.updatedAt()).isEqualTo(APPLIED_AT);
    }

    private static CardApplication applicationIn(ApplicationStatus status, int version) {
        return new CardApplication(
                "A00000001",
                "テスト 太郎",
                "テスト　タロウ",
                LocalDate.of(1990, 4, 1),
                new BigDecimal("450"),
                "テスト株式会社",
                "0000000000",
                status,
                APPLIED_AT,
                APPLIED_AT,
                version);
    }
}
