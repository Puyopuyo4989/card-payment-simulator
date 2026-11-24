package com.example.cardpayment.revolving.domain;

import com.example.cardpayment.common.domain.ScheduleRow;
import java.math.BigDecimal;
import java.util.List;

/**
 * シミュレーション結果（data-model.md 2）。金額はすべて円単位で、小数なし。
 *
 * @param paymentCount 完済までの支払回数（1〜360）
 * @param totalFee 手数料の合計
 * @param totalPayment 支払総額
 * @param schedule 返済表（1回目から完済する回まで）
 */
public record RevolvingResult(
        int paymentCount,
        BigDecimal totalFee,
        BigDecimal totalPayment,
        List<ScheduleRow> schedule) {}
