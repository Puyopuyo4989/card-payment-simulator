package com.example.cardpayment.revolving.web;

import com.example.cardpayment.revolving.domain.RevolvingCalculator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** リボ払いの支払シミュレーションの API（contracts/revolving-simulation-api.md）。計算だけで、何も保存しない。 */
@RestController
public class RevolvingSimulationController {

    private final RevolvingInputValidator validator = new RevolvingInputValidator();
    private final RevolvingCalculator calculator = new RevolvingCalculator();

    @PostMapping("/api/revolving-simulations")
    public RevolvingSimulationResponse simulate(@RequestBody RevolvingSimulationRequest request) {
        return RevolvingSimulationResponse.from(calculator.calculate(validator.validate(request)));
    }
}
