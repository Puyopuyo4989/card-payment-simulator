package com.example.cardpayment.revolving.web;

import static com.example.cardpayment.common.web.InputRules.strip;

import com.example.cardpayment.revolving.domain.RevolvingCondition;
import java.math.BigDecimal;

/** 入力の文字列を確かめて RevolvingCondition に変える（data-model.md 1）。 */
public class RevolvingInputValidator {

    public RevolvingCondition validate(RevolvingSimulationRequest request) {
        return new RevolvingCondition(
                new BigDecimal(strip(request.balance())),
                new BigDecimal(strip(request.monthlyPayment())),
                new BigDecimal(strip(request.annualRate())));
    }
}
