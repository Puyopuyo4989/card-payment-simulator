package com.example.cardpayment.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 分割払いとリボ払いに共通の、入力の文字列の解釈（data-model.md 1、002 の research.md R6）。 */
class InputRulesTest {

    private static final String RATE_REQUIRED = "実質年率を入力してください";
    private static final String RATE_FORMAT = "実質年率は半角数字で、小数第1位までで入力してください（例：12.5）";
    private static final String RATE_RANGE = "実質年率は0.0%以上15.0%以下で入力してください";

    @Test
    void 未指定は空文字にする() {
        assertThat(InputRules.strip(null)).isEmpty();
    }

    @Test
    void 前後の空白を取り除く() {
        assertThat(InputRules.strip(" 100 ")).isEqualTo("100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01000", "10000000"})
    void 半角数字だけなら整数の形式(String value) {
        assertThat(InputRules.isInteger(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.5", "-1", "１", "1,000", "abc", "1 0"})
    void 半角数字だけでなければ整数の形式でない(String value) {
        assertThat(InputRules.isInteger(value)).isFalse();
    }

    @Test
    void 長さが20文字ちょうどなら長すぎない() {
        assertThat(InputRules.isTooLong("1".repeat(20))).isFalse();
    }

    @Test
    void 長さが21文字なら長すぎる() {
        assertThat(InputRules.isTooLong("1".repeat(21))).isTrue();
    }

    @Test
    void 実質年率が空ならエラー() {
        assertThat(annualRateErrorsOf(""))
                .containsExactly(new FieldError("annualRate", RATE_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.25", "15.00", "-1", "-0.1", ".5", "abc"})
    void 実質年率が小数第1位までの半角数字でなければ形式のエラー(String annualRate) {
        assertThat(annualRateErrorsOf(annualRate))
                .containsExactly(new FieldError("annualRate", RATE_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"15.1", "16"})
    void 実質年率が範囲外なら範囲のエラー(String annualRate) {
        assertThat(annualRateErrorsOf(annualRate))
                .containsExactly(new FieldError("annualRate", RATE_RANGE));
    }

    @Test
    void 実質年率が形式に合っていて21文字以上なら範囲のエラー() {
        assertThat(annualRateErrorsOf("0".repeat(17) + "15.0"))
                .containsExactly(new FieldError("annualRate", RATE_RANGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "15.0"})
    void 実質年率の下限と上限ちょうどは受け付ける(String annualRate) {
        assertThat(annualRateErrorsOf(annualRate)).isEmpty();
    }

    @Test
    void 実質年率のエラーは渡したリストの後ろに足す() {
        List<FieldError> errors = new ArrayList<>();
        errors.add(new FieldError("amount", "利用金額を入力してください"));

        InputRules.checkAnnualRate("", errors);

        assertThat(errors)
                .containsExactly(
                        new FieldError("amount", "利用金額を入力してください"),
                        new FieldError("annualRate", RATE_REQUIRED));
    }

    private static List<FieldError> annualRateErrorsOf(String annualRate) {
        List<FieldError> errors = new ArrayList<>();
        InputRules.checkAnnualRate(annualRate, errors);
        return errors;
    }
}
