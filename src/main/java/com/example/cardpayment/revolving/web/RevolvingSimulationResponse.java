package com.example.cardpayment.revolving.web;

import com.example.cardpayment.common.web.ScheduleRowResponse;
import com.example.cardpayment.revolving.domain.RevolvingResult;
import java.math.BigDecimal;
import java.util.List;

/** API の成功のレスポンス（contracts の 200）。金額は円単位の整数。 */
public record RevolvingSimulationResponse(
        int paymentCount,
        BigDecimal totalFee,
        BigDecimal totalPayment,
        List<ScheduleRowResponse> schedule) {

    static RevolvingSimulationResponse from(RevolvingResult result) {
        return new RevolvingSimulationResponse(
                result.paymentCount(),
                result.totalFee(),
                result.totalPayment(),
                result.schedule().stream().map(ScheduleRowResponse::from).toList());
    }
}
