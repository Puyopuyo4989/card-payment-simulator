package com.example.cardpayment.revolving.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import com.example.cardpayment.revolving.domain.RevolvingCondition;
import com.example.cardpayment.revolving.domain.RevolvingNotPayableException;
import com.example.cardpayment.revolving.domain.RevolvingNotPayableException.Reason;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 入力の文字列を RevolvingCondition に変え、正しくない入力はエラーにする（data-model.md 1、contracts のメッセージの一覧）。 */
class RevolvingInputValidatorTest {

    private static final String BALANCE_REQUIRED = "利用残高を入力してください";
    private static final String BALANCE_FORMAT = "利用残高は半角数字（整数）で入力してください";
    private static final String BALANCE_RANGE = "利用残高は1,000円以上10,000,000円以下で入力してください";
    private static final String PAYMENT_REQUIRED = "毎月の支払額を入力してください";
    private static final String PAYMENT_FORMAT = "毎月の支払額は半角数字（整数）で入力してください";
    private static final String PAYMENT_RANGE = "毎月の支払額は1,000円以上10,000,000円以下の、1,000円単位の額で入力してください";
    private static final String PAYMENT_OVER_BALANCE = "毎月の支払額は利用残高以下で入力してください";
    private static final String PAYMENT_FEE_NOT_COVERED =
            "毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください";
    private static final String PAYMENT_TOO_MANY = "完済までの回数が360回を超えます。毎月の支払額を増やしてください";
    private static final String RATE_REQUIRED = "実質年率を入力してください";
    private static final String RATE_FORMAT = "実質年率は半角数字で、小数第1位までで入力してください（例：12.5）";
    private static final String RATE_RANGE = "実質年率は0.0%以上15.0%以下で入力してください";

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

    // 利用残高（FR-002、spec US3 の1・2）

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 利用残高が未入力ならエラー(String balance) {
        assertThat(errorsOf(balance, "1000", "15.0"))
                .containsExactly(new FieldError("balance", BALANCE_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000.5", "-1000", "100,000", "１０００", "abc"})
    void 利用残高が半角数字でなければ形式のエラー(String balance) {
        assertThat(errorsOf(balance, "1000", "15.0"))
                .containsExactly(new FieldError("balance", BALANCE_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "10000001", "100000000000000000000"})
    void 利用残高が範囲外なら範囲のエラー(String balance) {
        assertThat(errorsOf(balance, "1000", "15.0"))
                .containsExactly(new FieldError("balance", BALANCE_RANGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000", "10000000"})
    void 利用残高の下限と上限ちょうどは受け付ける(String balance) {
        assertThat(errorsOf(balance, "1000", "0.0")).isEmpty();
    }

    // 毎月の支払額（FR-004、spec US3 の3、エッジケース）

    @ParameterizedTest
    @NullAndEmptySource
    void 毎月の支払額が未入力ならエラー(String monthlyPayment) {
        assertThat(errorsOf("100000", monthlyPayment, "15.0"))
                .containsExactly(new FieldError("monthlyPayment", PAYMENT_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000.5", "-1000", "abc"})
    void 毎月の支払額が半角数字でなければ形式のエラー(String monthlyPayment) {
        assertThat(errorsOf("100000", monthlyPayment, "15.0"))
                .containsExactly(new FieldError("monthlyPayment", PAYMENT_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "1500", "10000001", "10001000", "100000000000000000000"})
    void 毎月の支払額が範囲外か1000円単位でなければ範囲のエラー(String monthlyPayment) {
        assertThat(errorsOf("10000000", monthlyPayment, "0.0"))
                .containsExactly(new FieldError("monthlyPayment", PAYMENT_RANGE));
    }

    @Test
    void 毎月の支払額の下限ちょうどは受け付ける() {
        assertThat(errorsOf("100000", "1000", "0.0")).isEmpty();
    }

    @Test
    void 毎月の支払額が利用残高を超えたらエラー() {
        assertThat(errorsOf("10000", "11000", "15.0"))
                .containsExactly(new FieldError("monthlyPayment", PAYMENT_OVER_BALANCE));
    }

    @Test
    void 毎月の支払額が利用残高と同じなら受け付ける() {
        assertThat(errorsOf("10000", "10000", "15.0")).isEmpty();
    }

    @Test
    void 利用残高が未入力なら利用残高以下かどうかは確かめない() {
        assertThat(errorsOf("", "20000000", "15.0"))
                .containsExactly(
                        new FieldError("balance", BALANCE_REQUIRED),
                        new FieldError("monthlyPayment", PAYMENT_RANGE));
    }

    @Test
    void 利用残高が範囲外なら利用残高以下かどうかは確かめない() {
        assertThat(errorsOf("999", "5000", "15.0"))
                .containsExactly(new FieldError("balance", BALANCE_RANGE));
    }

    // 実質年率（FR-003。分割払いと同じルールとメッセージ）

    @ParameterizedTest
    @NullAndEmptySource
    void 実質年率が未入力ならエラー(String annualRate) {
        assertThat(errorsOf("100000", "10000", annualRate))
                .containsExactly(new FieldError("annualRate", RATE_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.25", "15.00", "-1", ".5", "abc"})
    void 実質年率が小数第1位までの半角数字でなければ形式のエラー(String annualRate) {
        assertThat(errorsOf("100000", "10000", annualRate))
                .containsExactly(new FieldError("annualRate", RATE_FORMAT));
    }

    @Test
    void 実質年率が範囲外なら範囲のエラー() {
        assertThat(errorsOf("100000", "10000", "15.1"))
                .containsExactly(new FieldError("annualRate", RATE_RANGE));
    }

    @Test
    void 誤っている項目はすべて決まった順で返す() {
        // spec US3 の7
        assertThat(errorsOf("", "1500", "12.25"))
                .containsExactly(
                        new FieldError("balance", BALANCE_REQUIRED),
                        new FieldError("monthlyPayment", PAYMENT_RANGE),
                        new FieldError("annualRate", RATE_FORMAT));
    }

    // 払い終わらない理由を、毎月の支払額の入力エラーに変える（research.md R4）

    @Test
    void 毎月の支払額が1回目の手数料以下なら毎月の支払額のエラーにする() {
        var exception =
                validator.toInputError(new RevolvingNotPayableException(Reason.FEE_NOT_COVERED));

        assertThat(exception.errors())
                .containsExactly(new FieldError("monthlyPayment", PAYMENT_FEE_NOT_COVERED));
    }

    @Test
    void 完済までの回数が360回を超えるなら毎月の支払額のエラーにする() {
        var exception =
                validator.toInputError(new RevolvingNotPayableException(Reason.TOO_MANY_PAYMENTS));

        assertThat(exception.errors())
                .containsExactly(new FieldError("monthlyPayment", PAYMENT_TOO_MANY));
    }

    /** エラーがなければ空のリストを返す。 */
    private List<FieldError> errorsOf(String balance, String monthlyPayment, String annualRate) {
        var exception =
                catchThrowableOfType(
                        InputValidationException.class,
                        () ->
                                validator.validate(
                                        new RevolvingSimulationRequest(
                                                balance, monthlyPayment, annualRate)));
        return exception == null ? List.of() : exception.errors();
    }
}
