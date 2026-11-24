package com.example.cardpayment.revolving.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** POST /api/revolving-simulations（contracts/revolving-simulation-api.md）。 */
@WebMvcTest(RevolvingSimulationController.class)
class RevolvingSimulationControllerTest {

    @Autowired private MockMvcTester mvc;

    @Test
    void 正しい入力で回数と手数料の合計と支払総額と返済表を返す() {
        var result =
                simulate(
                        """
                        {"balance":"100000","monthlyPayment":"10000","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.paymentCount").isEqualTo(11);
        assertThat(result).bodyJson().extractingPath("$.totalFee").isEqualTo(7497);
        assertThat(result).bodyJson().extractingPath("$.totalPayment").isEqualTo(107497);
        assertThat(result).bodyJson().extractingPath("$.schedule").asArray().isNotNull();
    }

    @Test
    void 返済表を1回目から完済する回まで返す() {
        var result =
                simulate(
                        """
                        {"balance":"100000","monthlyPayment":"10000","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.schedule").asArray().hasSize(11);
        // 項目名と、金額が整数のまま返ることを、本文の文字列で確かめる（contracts の例）
        assertThat(result)
                .body()
                .asString()
                .contains(
                        "{\"number\":1,\"payment\":10000,\"principal\":8750,\"fee\":1250,\"balanceAfter\":91250}")
                .contains(
                        "{\"number\":11,\"payment\":7497,\"principal\":7405,\"fee\":92,\"balanceAfter\":0}");
    }

    @Test
    void 金額は指数表記や小数点を付けず整数のまま返す() {
        var result =
                simulate(
                        """
                        {"balance":"100000","monthlyPayment":"10000","annualRate":"0.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        // JSON を読み込むと 1E+5 と 100000 の違いが消えるので、本文の文字列で確かめる
        assertThat(result)
                .body()
                .asString()
                .contains("\"totalPayment\":100000")
                .doesNotContain("1E+5")
                .doesNotContain("100000.0");
    }

    private MvcTestResult simulate(String body) {
        return mvc.post()
                .uri("/api/revolving-simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
