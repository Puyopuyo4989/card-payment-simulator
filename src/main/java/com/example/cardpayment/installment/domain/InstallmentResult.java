package com.example.cardpayment.installment.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * シミュレーション結果（data-model.md 2）。金額はすべて円単位で、小数なし。
 *
 * @param totalFee 手数料の合計
 * @param totalPayment 支払総額
 * @param firstPayment 初回の支払額
 * @param subsequentPayment 2回目以降の支払額
 * @param schedule 返済表（1回目から最終回まで）
 */
public record InstallmentResult(
        BigDecimal totalFee,
        BigDecimal totalPayment,
        BigDecimal firstPayment,
        BigDecimal subsequentPayment,
        List<ScheduleRow> schedule) {}
