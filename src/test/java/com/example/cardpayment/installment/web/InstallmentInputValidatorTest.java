package com.example.cardpayment.installment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.cardpayment.installment.domain.InstallmentCondition;
import com.example.cardpayment.installment.web.InstallmentInputException.FieldError;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 入力の文字列を InstallmentCondition に変え、正しくない入力はエラーにする（data-model.md 1、contracts のメッセージの一覧）。 */
class InstallmentInputValidatorTest {

    private static final String AMOUNT_REQUIRED = "利用金額を入力してください";
    private static final String AMOUNT_FORMAT = "利用金額は半角数字（整数）で入力してください";
    private static final String AMOUNT_RANGE = "利用金額は1,000円以上10,000,000円以下で入力してください";
    private static final String COUNT_REQUIRED = "支払回数を選んでください";
    private static final String COUNT_INVALID = "支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください";
    private static final String RATE_REQUIRED = "実質年率を入力してください";
    private static final String RATE_FORMAT = "実質年率は半角数字で、小数第1位までで入力してください（例：12.5）";
    private static final String RATE_RANGE = "実質年率は0.0%以上15.0%以下で入力してください";

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 利用金額が未入力ならエラー(String amount) {
        assertThat(errorsOf(amount, "12", "15.0"))
                .containsExactly(new FieldError("amount", AMOUNT_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000.5", "-1000", "100,000", "１０００", "abc"})
    void 利用金額が半角数字の整数でなければ形式のエラー(String amount) {
        assertThat(errorsOf(amount, "12", "15.0"))
                .containsExactly(new FieldError("amount", AMOUNT_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "10000001"})
    void 利用金額が範囲外なら範囲のエラー(String amount) {
        assertThat(errorsOf(amount, "12", "15.0"))
                .containsExactly(new FieldError("amount", AMOUNT_RANGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000", "10000000"})
    void 利用金額の下限と上限ちょうどは受け付ける(String amount) {
        assertThat(errorsOf(amount, "12", "15.0")).isEmpty();
    }

    @Test
    void 支払回数の先頭の0を受け付ける() {
        var condition =
                validator.validate(new InstallmentSimulationRequest("100000", "012", "15.0"));

        assertThat(condition.paymentCount()).isEqualTo(12);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 支払回数が未入力ならエラー(String paymentCount) {
        assertThat(errorsOf("100000", paymentCount, "15.0"))
                .containsExactly(new FieldError("paymentCount", COUNT_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "0", "00", "abc", "12.0", "-12", "99999999999"})
    void 支払回数が一覧になければエラー(String paymentCount) {
        assertThat(errorsOf("100000", paymentCount, "15.0"))
                .containsExactly(new FieldError("paymentCount", COUNT_INVALID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "5", "6", "10", "12", "15", "18", "20", "24", "30", "36"})
    void 一覧の支払回数は受け付ける(String paymentCount) {
        assertThat(errorsOf("100000", paymentCount, "15.0")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 実質年率が未入力ならエラー(String annualRate) {
        assertThat(errorsOf("100000", "12", annualRate))
                .containsExactly(new FieldError("annualRate", RATE_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.25", "15.00", "-1", "-0.1", ".5", "abc"})
    void 実質年率が小数第1位までの半角数字でなければ形式のエラー(String annualRate) {
        assertThat(errorsOf("100000", "12", annualRate))
                .containsExactly(new FieldError("annualRate", RATE_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"15.1", "16"})
    void 実質年率が範囲外なら範囲のエラー(String annualRate) {
        assertThat(errorsOf("100000", "12", annualRate))
                .containsExactly(new FieldError("annualRate", RATE_RANGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "0", "15.0"})
    void 実質年率の下限と上限ちょうどは受け付ける(String annualRate) {
        assertThat(errorsOf("100000", "12", annualRate)).isEmpty();
    }

    @Test
    void 長さが上限の20文字ちょうどなら受け付ける() {
        var condition =
                validator.validate(
                        new InstallmentSimulationRequest(
                                "0".repeat(16) + "1000",
                                "0".repeat(18) + "12",
                                "0".repeat(16) + "15.0"));

        assertThat(condition)
                .isEqualTo(
                        new InstallmentCondition(
                                new BigDecimal("1000"), 12, new BigDecimal("15.0")));
    }

    @Test
    void 長さが上限の20文字を超えたら形式のエラー() {
        assertThat(
                        errorsOf(
                                "0".repeat(17) + "1000",
                                "0".repeat(19) + "12",
                                "0".repeat(17) + "15.0"))
                .containsExactly(
                        new FieldError("amount", AMOUNT_FORMAT),
                        new FieldError("paymentCount", COUNT_INVALID),
                        new FieldError("annualRate", RATE_FORMAT));
    }

    @Test
    void 長さは前後の空白を取り除いてから数える() {
        assertThat(errorsOf("  " + "0".repeat(16) + "1000  ", "12", "15.0")).isEmpty();
    }

    @Test
    void 誤っている項目はすべて決まった順で返す() {
        assertThat(errorsOf("", "4", "12.25"))
                .containsExactly(
                        new FieldError("amount", AMOUNT_REQUIRED),
                        new FieldError("paymentCount", COUNT_INVALID),
                        new FieldError("annualRate", RATE_FORMAT));
    }

    /** エラーがなければ空のリストを返す。 */
    private List<FieldError> errorsOf(String amount, String paymentCount, String annualRate) {
        var exception =
                catchThrowableOfType(
                        InstallmentInputException.class,
                        () ->
                                validator.validate(
                                        new InstallmentSimulationRequest(
                                                amount, paymentCount, annualRate)));
        return exception == null ? List.of() : exception.errors();
    }
}
