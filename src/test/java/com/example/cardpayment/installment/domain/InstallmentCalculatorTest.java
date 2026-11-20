package com.example.cardpayment.installment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 要約の4項目の計算。期待値は spec の受け入れシナリオと、人が別途計算して確かめた値（tasks.md T016）。 */
class InstallmentCalculatorTest {

    private final InstallmentCalculator calculator = new InstallmentCalculator();

    @ParameterizedTest(name = "{0}円・{1}回・{2}% → 手数料{3}・総額{4}・初回{5}・2回目以降{6}")
    @CsvSource({
        // spec US1 の1
        "100000,   12, 15.0, 8309,    108309,   9034,    9025",
        // spec US1 の3（年率0%）
        "100000,    3, 0.0,  0,       100000,   33334,   33333",
        // spec US2 の6
        "1000,     36, 15.0, 247,     1247,     57,      34",
        // 月利が割り切れない
        "100000,   12, 14.9, 8253,    108253,   9022,    9021",
        // 利用金額と回数の上限
        "10000000, 36, 15.0, 2479518, 12479518, 346663,  346653",
        // 年率が0%でないのに初回と2回目以降が同じ
        "100000,    3, 15.0, 2510,    102510,   34170,   34170",
        // 年率が0%でない下限
        "10000000,  3, 0.1,  1666,    10001666, 3333890, 3333888",
    })
    void 要約の4項目を計算する(
            String amount,
            int paymentCount,
            String annualRate,
            String totalFee,
            String totalPayment,
            String firstPayment,
            String subsequentPayment) {
        var result = calculator.calculate(condition(amount, paymentCount, annualRate));

        assertThat(result.totalFee()).isEqualByComparingTo(totalFee);
        assertThat(result.totalPayment()).isEqualByComparingTo(totalPayment);
        assertThat(result.firstPayment()).isEqualByComparingTo(firstPayment);
        assertThat(result.subsequentPayment()).isEqualByComparingTo(subsequentPayment);
    }

    @ParameterizedTest(name = "{0}円・{1}回・{2}%")
    @CsvSource({"100000, 12, 15.0", "100000, 3, 0.0", "1000, 36, 15.0", "100000, 12, 14.9"})
    void 支払総額は利用金額と手数料の合計を足したもの(String amount, int paymentCount, String annualRate) {
        var result = calculator.calculate(condition(amount, paymentCount, annualRate));

        assertThat(result.totalPayment()).isEqualTo(new BigDecimal(amount).add(result.totalFee()));
    }

    @ParameterizedTest(name = "{0}円・{1}回・{2}%")
    @CsvSource({"100000, 12, 15.0", "100000, 3, 0.0", "1000, 36, 15.0", "100000, 12, 14.9"})
    void すべての金額は小数の桁を持たない(String amount, int paymentCount, String annualRate) {
        var result = calculator.calculate(condition(amount, paymentCount, annualRate));

        assertThat(result.totalFee().scale()).isZero();
        assertThat(result.totalPayment().scale()).isZero();
        assertThat(result.firstPayment().scale()).isZero();
        assertThat(result.subsequentPayment().scale()).isZero();
    }

    private static InstallmentCondition condition(
            String amount, int paymentCount, String annualRate) {
        return new InstallmentCondition(
                new BigDecimal(amount), paymentCount, new BigDecimal(annualRate));
    }
}
