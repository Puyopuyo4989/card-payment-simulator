package com.example.cardpayment.installment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
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

    @Test
    void 返済表の全12行が仕様の表と一致する() {
        // spec US2 の受け入れシナリオ5（100,000円・12回・15.0%）
        var result = calculator.calculate(condition("100000", 12, "15.0"));

        assertThat(result.schedule())
                .containsExactly(
                        // 回, 支払額, 手数料, 元金, 支払後の残高（spec の表の列の順）
                        row(1, 9034, 1250, 7784, 92216),
                        row(2, 9025, 1152, 7873, 84343),
                        row(3, 9025, 1054, 7971, 76372),
                        row(4, 9025, 954, 8071, 68301),
                        row(5, 9025, 853, 8172, 60129),
                        row(6, 9025, 751, 8274, 51855),
                        row(7, 9025, 648, 8377, 43478),
                        row(8, 9025, 543, 8482, 34996),
                        row(9, 9025, 437, 8588, 26408),
                        row(10, 9025, 330, 8695, 17713),
                        row(11, 9025, 221, 8804, 8909),
                        row(12, 9025, 116, 8909, 0));
    }

    @Test
    void 毎回の手数料の切り捨て分は最終回の手数料にまとめて加わる() {
        // spec US2 の6（1,000円・36回・15.0%）。最終回の手数料が元金より大きくなる
        var result = calculator.calculate(condition("1000", 36, "15.0"));

        assertThat(result.schedule()).hasSize(36);
        assertThat(result.schedule().getLast()).isEqualTo(row(36, 34, 30, 4, 0));
    }

    @Test
    void 年率0パーセントの返済表は手数料がすべて0円() {
        // spec US1 の3（100,000円・3回・0.0%）
        var result = calculator.calculate(condition("100000", 3, "0.0"));

        assertThat(result.schedule())
                .hasSize(3)
                .allSatisfy(row -> assertThat(row.fee()).isEqualByComparingTo("0"));
    }

    @ParameterizedTest(name = "{0}円・{1}回・{2}%")
    @CsvSource({
        // 月利が割り切れない
        "100000,   12, 14.9, 1241,   7781,    92219,   116,  8905,    0",
        // 利用金額と回数の上限
        "10000000, 36, 15.0, 125000, 221663,  9778337, 4304, 342349,  0",
        // 年率が0%でないのに初回と2回目以降が同じ
        "100000,    3, 15.0, 1250,   32920,   67080,   422,  33748,   0",
        // 年率が0%でない下限
        "10000000,  3, 0.1,  833,    3333057, 6666943, 278,  3333610, 0",
    })
    void 返済表の1回目と最終回の手数料と元金と残高(
            String amount,
            int paymentCount,
            String annualRate,
            String firstFee,
            String firstPrincipal,
            String firstBalanceAfter,
            String lastFee,
            String lastPrincipal,
            String lastBalanceAfter) {
        var schedule = calculator.calculate(condition(amount, paymentCount, annualRate)).schedule();

        assertThat(schedule).hasSize(paymentCount);
        var first = schedule.getFirst();
        assertThat(first.fee()).isEqualByComparingTo(firstFee);
        assertThat(first.principal()).isEqualByComparingTo(firstPrincipal);
        assertThat(first.balanceAfter()).isEqualByComparingTo(firstBalanceAfter);
        var last = schedule.getLast();
        assertThat(last.fee()).isEqualByComparingTo(lastFee);
        assertThat(last.principal()).isEqualByComparingTo(lastPrincipal);
        assertThat(last.balanceAfter()).isEqualByComparingTo(lastBalanceAfter);
    }

    /** spec の返済表の列の順（回・支払額・手数料・元金・支払後の残高）で1行を作る。金額は小数なし。 */
    private static ScheduleRow row(
            int number, long payment, long fee, long principal, long balanceAfter) {
        return new ScheduleRow(
                number,
                BigDecimal.valueOf(payment),
                BigDecimal.valueOf(principal),
                BigDecimal.valueOf(fee),
                BigDecimal.valueOf(balanceAfter));
    }

    private static InstallmentCondition condition(
            String amount, int paymentCount, String annualRate) {
        return new InstallmentCondition(
                new BigDecimal(amount), paymentCount, new BigDecimal(annualRate));
    }
}
