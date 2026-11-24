package com.example.cardpayment.common.web;

import com.example.cardpayment.common.domain.ScheduleRow;
import java.math.BigDecimal;

/** API で返す返済表の1行（各機能の contracts の schedule[]）。ドメインの ScheduleRow の項目名を変えても、API の契約が変わらないように分ける。 */
public record ScheduleRowResponse(
        int number,
        BigDecimal payment,
        BigDecimal principal,
        BigDecimal fee,
        BigDecimal balanceAfter) {

    public static ScheduleRowResponse from(ScheduleRow row) {
        return new ScheduleRowResponse(
                row.number(), row.payment(), row.principal(), row.fee(), row.balanceAfter());
    }
}
