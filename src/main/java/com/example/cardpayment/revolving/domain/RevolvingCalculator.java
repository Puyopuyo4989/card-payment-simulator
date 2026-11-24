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

    public RevolvingResult calculate(RevolvingCondition condition) {
        BigDecimal monthlyPayment = condition.monthlyPayment();
        // 実質年率は小数第1位までなので、10倍すると整数になる。整数でなければ例外にする（丸めない）
        BigDecimal k =
                BigDecimal.valueOf(condition.annualRate().multiply(BigDecimal.TEN).intValueExact());

        List<ScheduleRow> schedule = new ArrayList<>();
        BigDecimal balance = condition.balance();
        BigDecimal totalFee = BigDecimal.ZERO;
        while (balance.signum() > 0) {
            int number = schedule.size() + 1;
            BigDecimal fee = balance.multiply(k).divide(D, 0, RoundingMode.DOWN);
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
}
