package com.example.cardpayment.revolving.domain;

import com.example.cardpayment.common.domain.ScheduleRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * リボ払い（元利定額方式）の計算（spec の FR-006〜FR-008）。Spring に依存しない。
 *
 * <p>整数の値の BigDecimal だけで計算し、割り算は各回の手数料の 1円未満を切り捨てる箇所でだけ行う（research.md R2）。
 */
public class RevolvingCalculator {

    /** 月利 ＝ k ÷ D（k ＝ 実質年率 × 10）。 */
    private static final BigDecimal D = BigDecimal.valueOf(12_000);

    /** 完済までの回数の上限（spec FR-004）。 */
    private static final int MAX_PAYMENT_COUNT = 360;

    /**
     * 返済表を1回目から順に作る（data-model.md 4）。
     *
     * @throws RevolvingNotPayableException 毎月の支払額が1回目の手数料以下か、完済までの回数が360回を超えるとき。361回目は計算しない
     */
    public RevolvingResult calculate(RevolvingCondition condition) {
        BigDecimal monthlyPayment = condition.monthlyPayment();
        // 実質年率は小数第1位までなので、10倍すると整数になる。整数でなければ例外にする（丸めない）
        BigDecimal k =
                BigDecimal.valueOf(condition.annualRate().multiply(BigDecimal.TEN).intValueExact());

        // 残高は回を追うごとに減るので、1回目の手数料が払えれば、2回目以降の手数料も払える
        if (fee(condition.balance(), k).compareTo(monthlyPayment) >= 0) {
            throw new RevolvingNotPayableException(
                    RevolvingNotPayableException.Reason.FEE_NOT_COVERED);
        }

        List<ScheduleRow> schedule = new ArrayList<>();
        BigDecimal balance = condition.balance();
        BigDecimal totalFee = BigDecimal.ZERO;
        while (balance.signum() > 0) {
            if (schedule.size() == MAX_PAYMENT_COUNT) {
                throw new RevolvingNotPayableException(
                        RevolvingNotPayableException.Reason.TOO_MANY_PAYMENTS);
            }
            int number = schedule.size() + 1;
            BigDecimal fee = fee(balance, k);
            // 残高と手数料を合わせて毎月の支払額以下なら最終回で、その全額を払う（FR-007）
            BigDecimal payment = balance.add(fee).min(monthlyPayment);
            BigDecimal principal = payment.subtract(fee);
            balance = balance.subtract(principal);
            totalFee = totalFee.add(fee);
            schedule.add(new ScheduleRow(number, payment, principal, fee, balance));
        }

        return new RevolvingResult(
                schedule.size(),
                totalFee,
                condition.balance().add(totalFee),
                List.copyOf(schedule));
    }

    /** その回の手数料 ＝ 切り捨て(残高 × k ÷ D)（FR-007）。丸めはここだけ。 */
    private static BigDecimal fee(BigDecimal balance, BigDecimal k) {
        return balance.multiply(k).divide(D, 0, RoundingMode.DOWN);
    }
}
