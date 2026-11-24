package com.example.cardpayment.revolving.web;

import com.example.cardpayment.revolving.domain.RevolvingCalculator;
import com.example.cardpayment.revolving.domain.RevolvingNotPayableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** リボ払いの支払シミュレーションの API（contracts/revolving-simulation-api.md）。計算だけで、何も保存しない。 */
@RestController
public class RevolvingSimulationController {

    private final RevolvingInputValidator validator = new RevolvingInputValidator();
    private final RevolvingCalculator calculator = new RevolvingCalculator();

    /** 払い終わらないことは計算して初めて分かるので、計算が投げた理由を、毎月の支払額の入力エラーに変える（plan.md「入力チェック」）。 */
    @PostMapping("/api/revolving-simulations")
    public RevolvingSimulationResponse simulate(@RequestBody RevolvingSimulationRequest request) {
        var condition = validator.validate(request);
        try {
            return RevolvingSimulationResponse.from(calculator.calculate(condition));
        } catch (RevolvingNotPayableException exception) {
            throw validator.toInputError(exception);
        }
    }
}
