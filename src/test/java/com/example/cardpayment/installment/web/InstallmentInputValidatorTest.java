package com.example.cardpayment.installment.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.installment.domain.InstallmentCondition;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 正しい入力の文字列を InstallmentCondition に変える（data-model.md 1）。エラーの扱いは US3 で足す。 */
class InstallmentInputValidatorTest {

    private final InstallmentInputValidator validator = new InstallmentInputValidator();

    @Test
    void 文字列の3項目を条件に変える() {
        var condition =
                validator.validate(new InstallmentSimulationRequest("100000", "12", "15.0"));

        assertThat(condition)
                .isEqualTo(
                        new InstallmentCondition(
                                new BigDecimal("100000"), 12, new BigDecimal("15.0")));
    }

    @Test
    void 小数点のない実質年率を受け付ける() {
        var condition = validator.validate(new InstallmentSimulationRequest("100000", "12", "15"));

        assertThat(condition.annualRate()).isEqualByComparingTo("15");
    }

    @Test
    void 前後の空白を取り除く() {
        var condition =
                validator.validate(new InstallmentSimulationRequest(" 100000 ", " 12 ", " 15.0 "));

        assertThat(condition)
                .isEqualTo(
                        new InstallmentCondition(
                                new BigDecimal("100000"), 12, new BigDecimal("15.0")));
    }

    @Test
    void 利用金額の先頭の0を受け付ける() {
        var condition = validator.validate(new InstallmentSimulationRequest("01000", "12", "15.0"));

        assertThat(condition.amount()).isEqualTo(new BigDecimal("1000"));
    }
}
