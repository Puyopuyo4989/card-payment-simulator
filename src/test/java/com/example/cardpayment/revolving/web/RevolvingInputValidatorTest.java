package com.example.cardpayment.revolving.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.revolving.domain.RevolvingCondition;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 入力の文字列を RevolvingCondition に変え、正しくない入力はエラーにする（data-model.md 1、contracts のメッセージの一覧）。 */
class RevolvingInputValidatorTest {

    private final RevolvingInputValidator validator = new RevolvingInputValidator();

    @Test
    void 文字列の3項目を条件に変える() {
        var condition =
                validator.validate(new RevolvingSimulationRequest("100000", "10000", "15.0"));

        assertThat(condition)
                .isEqualTo(
                        new RevolvingCondition(
                                new BigDecimal("100000"),
                                new BigDecimal("10000"),
                                new BigDecimal("15.0")));
    }

    @Test
    void 小数点のない実質年率を受け付ける() {
        var condition = validator.validate(new RevolvingSimulationRequest("100000", "10000", "15"));

        assertThat(condition.annualRate()).isEqualByComparingTo("15");
    }

    @Test
    void 前後の空白を取り除く() {
        var condition =
                validator.validate(new RevolvingSimulationRequest(" 100000 ", " 10000 ", " 15.0 "));

        assertThat(condition)
                .isEqualTo(
                        new RevolvingCondition(
                                new BigDecimal("100000"),
                                new BigDecimal("10000"),
                                new BigDecimal("15.0")));
    }

    @Test
    void 先頭の0を受け付ける() {
        var condition =
                validator.validate(new RevolvingSimulationRequest("01000", "01000", "15.0"));

        assertThat(condition.balance()).isEqualByComparingTo("1000");
        assertThat(condition.monthlyPayment()).isEqualByComparingTo("1000");
    }
}
