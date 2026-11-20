package com.example.cardpayment.installment.domain;

import java.math.BigDecimal;

/**
 * 返済表の1行（data-model.md 3）。金額はすべて円単位で、小数なし。
 *
 * @param number 回（1〜支払回数）
 * @param payment 支払額
 * @param principal 元金
 * @param fee 手数料
 * @param balanceAfter 支払後の残高
 */
public record ScheduleRow(
        int number,
        BigDecimal payment,
        BigDecimal principal,
        BigDecimal fee,
        BigDecimal balanceAfter) {}
