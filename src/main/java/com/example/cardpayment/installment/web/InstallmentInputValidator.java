package com.example.cardpayment.installment.web;

import com.example.cardpayment.installment.domain.InstallmentCondition;
import java.math.BigDecimal;

/** 入力の文字列を解釈して InstallmentCondition に変える（data-model.md 1）。正しくない入力のチェックは US3 で足す。 */
public class InstallmentInputValidator {

    public InstallmentCondition validate(InstallmentSimulationRequest request) {
        return new InstallmentCondition(
                new BigDecimal(request.amount().strip()),
                Integer.parseInt(request.paymentCount().strip()),
                new BigDecimal(request.annualRate().strip()));
    }
}
