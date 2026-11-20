package com.example.cardpayment.installment.web;

import com.example.cardpayment.installment.domain.InstallmentResult;
import com.example.cardpayment.installment.domain.ScheduleRow;
import java.math.BigDecimal;
import java.util.List;

/** API の成功のレスポンス（contracts の 200）。金額は円単位の整数。 */
public record InstallmentSimulationResponse(
        BigDecimal totalFee,
        BigDecimal totalPayment,
        BigDecimal firstPayment,
        BigDecimal subsequentPayment,
        List<ScheduleRow> schedule) {

    static InstallmentSimulationResponse from(InstallmentResult result) {
        return new InstallmentSimulationResponse(
                result.totalFee(),
                result.totalPayment(),
                result.firstPayment(),
                result.subsequentPayment(),
                result.schedule());
    }
}
