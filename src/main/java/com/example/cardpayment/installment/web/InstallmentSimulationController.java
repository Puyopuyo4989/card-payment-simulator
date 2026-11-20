package com.example.cardpayment.installment.web;

import com.example.cardpayment.installment.domain.InstallmentCalculator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 分割払いの支払シミュレーションの API（contracts/installment-simulation-api.md）。計算だけで、何も保存しない。 */
@RestController
public class InstallmentSimulationController {

    private final InstallmentInputValidator validator = new InstallmentInputValidator();
    private final InstallmentCalculator calculator = new InstallmentCalculator();

    @PostMapping("/api/installment-simulations")
    public InstallmentSimulationResponse simulate(
            @RequestBody InstallmentSimulationRequest request) {
        return InstallmentSimulationResponse.from(
                calculator.calculate(validator.validate(request)));
    }
}
