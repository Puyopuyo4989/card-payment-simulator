package com.example.cardpayment.revolving.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.common.domain.ScheduleRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** リボ払い（元利定額方式）の計算。期待値は spec の受け入れシナリオ・エッジケースと、人が別途確かめた値（tasks.md T001）。 */
class RevolvingCalculatorTest {

    private final RevolvingCalculator calculator = new RevolvingCalculator();

    @ParameterizedTest(name = "{0}円・毎月{1}円・{2}% → {3}回・手数料{4}・総額{5}")
    @CsvSource({
        // spec US1 の1
        "100000,   10000,  15.0, 11,  7497,     107497",
        // spec US1 の2（年率0%）
        "100000,   10000,  0.0,  10,  0,        100000",
        // spec US2 の7（毎月の支払額が利用残高と同じで、年率が0%より大きい）
        "1000,     1000,   15.0, 2,   12,       1012",
        // spec のエッジケース（毎月の支払額が利用残高と同じで、年率0%なら1回で完済）
        "1000,     1000,   0.0,  1,   0,        1000",
    })
    void 完済までの回数と手数料の合計と支払総額を計算する(
            String balance,
            String monthlyPayment,
            String annualRate,
            int paymentCount,
            String totalFee,
            String totalPayment) {
        var result = calculator.calculate(condition(balance, monthlyPayment, annualRate));

        assertThat(result.paymentCount()).isEqualTo(paymentCount);
        assertThat(result.totalFee()).isEqualByComparingTo(totalFee);
        assertThat(result.totalPayment()).isEqualByComparingTo(totalPayment);
    }

    @Test
    void 利用残高の上限でも完済までの回数を計算する() {
        // spec US3 の6
        var result = calculator.calculate(condition("10000000", "127000", "15.0"));

        assertThat(result.paymentCount()).isEqualTo(335);
    }

    @Test
    void 支払総額は利用残高と手数料の合計を足した額() {
        var result = calculator.calculate(condition("100000", "10000", "15.0"));

        assertThat(result.totalPayment())
                .isEqualByComparingTo(new BigDecimal("100000").add(result.totalFee()));
    }

    @Test
    void 金額は小数なしで返す() {
        var result = calculator.calculate(condition("100000", "10000", "15.0"));

        assertThat(result.totalFee().scale()).isZero();
        assertThat(result.totalPayment().scale()).isZero();
    }

    @Test
    void 毎月の支払額を大きくしても回数と手数料の合計は多くならない() {
        // spec US1 の3。具体的な回数は期待値にせず、大小だけを確かめる
        var results =
                List.of("10000", "20000", "50000", "100000").stream()
                        .map(payment -> calculator.calculate(condition("100000", payment, "15.0")))
                        .toList();

        for (int i = 1; i < results.size(); i++) {
            var smaller = results.get(i - 1);
            var larger = results.get(i);
            assertThat(larger.paymentCount()).isLessThanOrEqualTo(smaller.paymentCount());
            assertThat(larger.totalFee()).isLessThanOrEqualTo(smaller.totalFee());
        }
        // 大小の比べ方が、すべて同じ値のときにも通ってしまわないよう、両端が違うことも確かめる
        assertThat(results.getLast().paymentCount()).isLessThan(results.getFirst().paymentCount());
    }

    @Test
    void 返済表の全行がspecの表と一致する() {
        // spec US2 の6（回・支払額・手数料・元金・支払後の残高）
        var schedule = calculator.calculate(condition("100000", "10000", "15.0")).schedule();

        assertThat(schedule)
                .containsExactly(
                        row(1, 10000, 1250, 8750, 91250),
                        row(2, 10000, 1140, 8860, 82390),
                        row(3, 10000, 1029, 8971, 73419),
                        row(4, 10000, 917, 9083, 64336),
                        row(5, 10000, 804, 9196, 55140),
                        row(6, 10000, 689, 9311, 45829),
                        row(7, 10000, 572, 9428, 36401),
                        row(8, 10000, 455, 9545, 26856),
                        row(9, 10000, 335, 9665, 17191),
                        row(10, 10000, 214, 9786, 7405),
                        row(11, 7497, 92, 7405, 0));
    }

    @Test
    void 毎月の支払額が利用残高と同じなら1回目の手数料の分が2回目に残る() {
        // spec US2 の7
        var schedule = calculator.calculate(condition("1000", "1000", "15.0")).schedule();

        assertThat(schedule).containsExactly(row(1, 1000, 12, 988, 12), row(2, 12, 0, 12, 0));
    }

    @Test
    void 年率0パーセントなら手数料はすべて0円() {
        // FR-008、spec US1 の2
        var schedule = calculator.calculate(condition("100000", "10000", "0.0")).schedule();

        assertThat(schedule).hasSize(10);
        assertThat(schedule)
                .allSatisfy(row -> {
                    assertThat(row.fee()).isEqualByComparingTo("0");
                    assertThat(row.payment()).isEqualByComparingTo("10000");
                });
        assertThat(schedule.getLast().balanceAfter()).isEqualByComparingTo("0");
    }

    @Test
    void 最終回より前の支払額は毎月の支払額と同じ() {
        // spec US2 の5
        var schedule = calculator.calculate(condition("100000", "10000", "15.0")).schedule();

        assertThat(schedule.subList(0, schedule.size() - 1))
                .allSatisfy(row -> assertThat(row.payment()).isEqualByComparingTo("10000"));
    }

    @Test
    void 完済までちょうど360回なら計算できる() {
        // spec のエッジケース。値は人が別途確かめたもの（tasks.md T001）
        var result = calculator.calculate(condition("949000", "12000", "15.0"));

        assertThat(result.paymentCount()).isEqualTo(360);
        assertThat(result.totalFee()).isEqualByComparingTo("3364823");
        assertThat(result.totalPayment()).isEqualByComparingTo("4313823");
        assertThat(result.schedule()).hasSize(360);
        assertThat(result.schedule().getLast().balanceAfter()).isEqualByComparingTo("0");
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

    private static RevolvingCondition condition(
            String balance, String monthlyPayment, String annualRate) {
        return new RevolvingCondition(
                new BigDecimal(balance),
                new BigDecimal(monthlyPayment),
                new BigDecimal(annualRate));
    }
}
