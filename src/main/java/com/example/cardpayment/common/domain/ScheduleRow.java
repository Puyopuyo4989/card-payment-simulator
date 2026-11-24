package com.example.cardpayment.common.domain;

import java.math.BigDecimal;

/**
 * 返済表の1行。分割払いとリボ払いで共通（各機能の data-model.md 3）。金額はすべて円単位で、小数なし。
 *
 * @param number 回（1〜最終回）
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
