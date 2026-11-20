package com.example.cardpayment.installment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 返済表の不変条件（SC-003、data-model.md 2・3）を、条件の範囲を広く組み合わせて確かめる。 */
class InstallmentCalculatorPropertyTest {

    private static final List<String> AMOUNTS =
            List.of("1000", "1001", "9999", "12345", "100000", "9999999", "10000000");
    private static final List<Integer> PAYMENT_COUNTS =
            List.of(3, 5, 6, 10, 12, 15, 18, 20, 24, 30, 36);
    private static final List<String> ANNUAL_RATES =
            List.of("0.0", "0.1", "7.5", "12.5", "14.9", "15.0");

    private final InstallmentCalculator calculator = new InstallmentCalculator();

    static Stream<Arguments> conditions() {
        return AMOUNTS.stream()
                .flatMap(amount ->
                        PAYMENT_COUNTS.stream()
                                .flatMap(paymentCount ->
                                        ANNUAL_RATES.stream()
                                                .map(annualRate ->
                                                        Arguments.of(
                                                                amount,
                                                                paymentCount,
                                                                annualRate))));
    }

    @ParameterizedTest(name = "{0}円・{1}回・{2}%")
    @MethodSource("conditions")
    void 返済表は不変条件を満たす(String amount, int paymentCount, String annualRate) {
        var result =
                calculator.calculate(
                        new InstallmentCondition(
                                new BigDecimal(amount), paymentCount, new BigDecimal(annualRate)));
        var schedule = result.schedule();

        assertThat(schedule).hasSize(paymentCount);
        assertThat(sum(schedule.stream().map(ScheduleRow::payment)))
                .as("支払額の合計 ＝ 支払総額（FR-008）")
                .isEqualByComparingTo(result.totalPayment());
        assertThat(sum(schedule.stream().map(ScheduleRow::principal)))
                .as("元金の合計 ＝ 利用金額（FR-009）")
                .isEqualByComparingTo(amount);
        assertThat(schedule.getLast().balanceAfter())
                .as("最終回の残高 ＝ 0（FR-010）")
                .isEqualByComparingTo("0");
        assertThat(sum(schedule.stream().map(ScheduleRow::fee)))
                .as("各回の手数料の合計 ＝ 手数料の合計")
                .isEqualByComparingTo(result.totalFee());
        assertThat(schedule)
                .allSatisfy(row -> {
                    assertThat(row.principal()).as("%d回目の元金", row.number()).isNotNegative();
                    assertThat(row.balanceAfter()).as("%d回目の残高", row.number()).isNotNegative();
                });
        assertThat(result.firstPayment()).isGreaterThanOrEqualTo(result.subsequentPayment());
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
