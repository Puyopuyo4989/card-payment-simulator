package com.example.cardpayment.installment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        return new InstallmentResult(
                totalFee, totalPayment, firstPayment, subsequentPayment, List.of());
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
