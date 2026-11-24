package com.example.cardpayment.revolving.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.common.domain.ScheduleRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 返済表の不変条件（SC-003、data-model.md 2・3）を、条件の範囲を広く組み合わせて確かめる。 */
class RevolvingCalculatorPropertyTest {

    private static final List<String> BALANCES =
            List.of("1000", "1001", "9999", "12345", "100000", "9999999", "10000000");
    private static final List<String> MONTHLY_PAYMENTS =
            List.of("1000", "5000", "10000", "127000", "1000000", "10000000");

    /** 利用残高が上限に近いときは、小さい支払額だと360回を超えるので、払い終わる支払額だけを使う（US3 で別に確かめる）。 */
    private static final List<String> MONTHLY_PAYMENTS_FOR_LARGE_BALANCE =
            List.of("127000", "1000000", "10000000");

    private static final List<String> ANNUAL_RATES =
            List.of("0.0", "0.1", "7.5", "12.5", "14.9", "15.0");

    private final RevolvingCalculator calculator = new RevolvingCalculator();

    static Stream<Arguments> conditions() {
        return BALANCES.stream()
                .flatMap(balance ->
                        paymentsFor(balance).stream()
                                .filter(payment -> isNotGreater(payment, balance))
                                .flatMap(payment ->
                                        ANNUAL_RATES.stream()
                                                .filter(rate ->
                                                        firstFeeIsCovered(balance, payment, rate))
                                                .map(rate ->
                                                        Arguments.of(balance, payment, rate))));
    }

    @ParameterizedTest(name = "{0}円・毎月{1}円・{2}%")
    @MethodSource("conditions")
    void 返済表は不変条件を満たす(String balance, String monthlyPayment, String annualRate) {
        var result =
                calculator.calculate(
                        new RevolvingCondition(
                                new BigDecimal(balance),
                                new BigDecimal(monthlyPayment),
                                new BigDecimal(annualRate)));
        var schedule = result.schedule();

        assertThat(schedule).hasSize(result.paymentCount());
        assertThat(result.paymentCount()).isBetween(1, 360);
        assertThat(sum(schedule.stream().map(ScheduleRow::payment)))
                .as("支払額の合計 ＝ 支払総額（FR-009）")
                .isEqualByComparingTo(result.totalPayment());
        assertThat(sum(schedule.stream().map(ScheduleRow::principal)))
                .as("元金の合計 ＝ 利用残高（FR-010）")
                .isEqualByComparingTo(balance);
        assertThat(schedule.getLast().balanceAfter())
                .as("最終回の残高 ＝ 0（FR-011）")
                .isEqualByComparingTo("0");
        assertThat(sum(schedule.stream().map(ScheduleRow::fee)))
                .as("各回の手数料の合計 ＝ 手数料の合計")
                .isEqualByComparingTo(result.totalFee());
        assertThat(schedule.subList(0, schedule.size() - 1))
                .allSatisfy(row ->
                        assertThat(row.payment())
                                .as("%d回目の支払額", row.number())
                                .isEqualByComparingTo(monthlyPayment));
        assertThat(schedule.getLast().payment())
                .isLessThanOrEqualTo(new BigDecimal(monthlyPayment));
        assertThat(schedule)
                .allSatisfy(row -> {
                    assertThat(row.fee()).as("%d回目の手数料", row.number()).isNotNegative();
                    assertThat(row.principal()).as("%d回目の元金", row.number()).isPositive();
                    assertThat(row.balanceAfter()).as("%d回目の残高", row.number()).isNotNegative();
                });
    }

    private static List<String> paymentsFor(String balance) {
        return new BigDecimal(balance).compareTo(new BigDecimal("9999999")) >= 0
                ? MONTHLY_PAYMENTS_FOR_LARGE_BALANCE
                : MONTHLY_PAYMENTS;
    }

    private static boolean isNotGreater(String payment, String balance) {
        return new BigDecimal(payment).compareTo(new BigDecimal(balance)) <= 0;
    }

    /** spec FR-004 の定義どおり、1回目の手数料（利用残高 × 月利 の切り捨て）より毎月の支払額が大きい。 */
    private static boolean firstFeeIsCovered(String balance, String payment, String annualRate) {
        BigDecimal firstFee =
                new BigDecimal(balance)
                        .multiply(new BigDecimal(annualRate))
                        .divide(new BigDecimal("1200"), 0, RoundingMode.DOWN);
        return new BigDecimal(payment).compareTo(firstFee) > 0;
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
