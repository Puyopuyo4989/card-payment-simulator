package com.example.cardpayment.installment.domain;

import com.example.cardpayment.common.domain.ScheduleRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 分割払いの計算（spec の FR-006・FR-007）。Spring に依存しない。
 *
 * <p>誤差を出さないよう、整数の値の BigDecimal だけで計算し、割り算は 1円未満を切り捨てる箇所でだけ行う（research.md R7）。
 */
public class InstallmentCalculator {

    /** 月利 ＝ k ÷ D（k ＝ 実質年率 × 10）。 */
    private static final BigDecimal D = BigDecimal.valueOf(12_000);

    public InstallmentResult calculate(InstallmentCondition condition) {
        BigDecimal amount = condition.amount();
        int paymentCount = condition.paymentCount();
        BigDecimal n = BigDecimal.valueOf(paymentCount);
        // 実質年率は小数第1位までなので、10倍すると整数になる。整数でなければ例外にする（丸めない）
        int k = condition.annualRate().multiply(BigDecimal.TEN).intValueExact();

        // 年率0%は FR-007 で手数料0円。支払額の求め方は FR-006 と同じになる
        BigDecimal totalFee =
                k == 0 ? BigDecimal.ZERO : totalFee(amount, paymentCount, BigDecimal.valueOf(k));
        BigDecimal totalPayment = amount.add(totalFee);
        BigDecimal subsequentPayment = totalPayment.divide(n, 0, RoundingMode.DOWN);
        BigDecimal firstPayment =
                totalPayment.subtract(subsequentPayment.multiply(n.subtract(BigDecimal.ONE)));

        List<ScheduleRow> schedule =
                schedule(amount, paymentCount, k, totalFee, firstPayment, subsequentPayment);
        return new InstallmentResult(
                totalFee, totalPayment, firstPayment, subsequentPayment, schedule);
    }

    /**
     * 返済表（FR-006）。手数料は、最終回の1つ前までは 切り捨て(前回の残高 × k ÷ D)、最終回は 手数料の合計 − それまでの手数料の合計。 年率0%（k ＝
     * 0）のときは、どの回の手数料も0円になる（FR-007）。
     */
    private static List<ScheduleRow> schedule(
            BigDecimal amount,
            int paymentCount,
            int k,
            BigDecimal totalFee,
            BigDecimal firstPayment,
            BigDecimal subsequentPayment) {
        List<ScheduleRow> rows = new ArrayList<>(paymentCount);
        BigDecimal balance = amount;
        BigDecimal feeSoFar = BigDecimal.ZERO;
        for (int number = 1; number <= paymentCount; number++) {
            BigDecimal payment = number == 1 ? firstPayment : subsequentPayment;
            BigDecimal fee =
                    number < paymentCount
                            ? balance.multiply(BigDecimal.valueOf(k))
                                    .divide(D, 0, RoundingMode.DOWN)
                            : totalFee.subtract(feeSoFar);
            BigDecimal principal = payment.subtract(fee);
            balance = balance.subtract(principal);
            feeSoFar = feeSoFar.add(fee);
            rows.add(new ScheduleRow(number, payment, principal, fee, balance));
        }
        return List.copyOf(rows);
    }

    /**
     * 切り捨て( (n × P × k × A − P × D × (A − B)) ÷ (D × (A − B)) )、A ＝ (D ＋ k)^n、B ＝ D^n（research.md
     * R7）。
     */
    private static BigDecimal totalFee(BigDecimal amount, int paymentCount, BigDecimal k) {
        BigDecimal n = BigDecimal.valueOf(paymentCount);
        BigDecimal a = D.add(k).pow(paymentCount);
        BigDecimal b = D.pow(paymentCount);
        BigDecimal aMinusB = a.subtract(b);

        BigDecimal numerator =
                n.multiply(amount)
                        .multiply(k)
                        .multiply(a)
                        .subtract(amount.multiply(D).multiply(aMinusB));
        return numerator.divide(D.multiply(aMinusB), 0, RoundingMode.DOWN);
    }
}
