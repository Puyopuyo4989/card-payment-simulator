package com.example.cardpayment.installment.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** POST /api/installment-simulations（contracts/installment-simulation-api.md）。 */
@WebMvcTest(InstallmentSimulationController.class)
class InstallmentSimulationControllerTest {

    @Autowired private MockMvcTester mvc;

    @Test
    void 正しい入力で要約の4項目と返済表を返す() {
        var result =
                simulate(
                        """
                        {"amount":"100000","paymentCount":"12","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.totalFee").isEqualTo(8309);
        assertThat(result).bodyJson().extractingPath("$.totalPayment").isEqualTo(108309);
        assertThat(result).bodyJson().extractingPath("$.firstPayment").isEqualTo(9034);
        assertThat(result).bodyJson().extractingPath("$.subsequentPayment").isEqualTo(9025);
        assertThat(result).bodyJson().extractingPath("$.schedule").asArray().isNotNull();
    }

    @Test
    void 返済表を1回目から最終回まで返す() {
        var result =
                simulate(
                        """
                        {"amount":"100000","paymentCount":"12","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.schedule").asArray().hasSize(12);
        // 項目名と、金額が整数のまま返ることを、本文の文字列で確かめる（contracts の例）
        assertThat(result)
                .body()
                .asString()
                .contains(
                        "{\"number\":1,\"payment\":9034,\"principal\":7784,\"fee\":1250,\"balanceAfter\":92216}");
        assertThat(result).bodyJson().extractingPath("$.schedule[11].number").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.schedule[11].balanceAfter").isEqualTo(0);
    }

    @Test
    void 金額は指数表記や小数点を付けず整数のまま返す() {
        var result =
                simulate(
                        """
                        {"amount":"100000","paymentCount":"3","annualRate":"0.0"}
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

    @Test
    void 入力に誤りがあれば400で項目ごとのエラーを返す() {
        var result =
                simulate(
                        """
                        {"amount":"","paymentCount":"4","annualRate":"12.25"}
                        """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("about:blank");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("入力に誤りがあります");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.errors").asArray().hasSize(3);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("amount");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("利用金額を入力してください");
        assertThat(result).bodyJson().extractingPath("$.errors[1].field").isEqualTo("paymentCount");
        assertThat(result).bodyJson().extractingPath("$.errors[2].field").isEqualTo("annualRate");
        // 誤った計算結果を返さない（SC-004）
        assertThat(result)
                .body()
                .asString()
                .doesNotContain("totalFee")
                .doesNotContain("totalPayment")
                .doesNotContain("schedule");
    }

    @Test
    void 本文がJSONとして読めなければ400で形式の誤りを返す() {
        var result = simulate("{\"amount\":");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("リクエストの形式が正しくありません");
        assertThat(result).bodyJson().doesNotHavePath("$.errors");
    }

    @Test
    void 項目がJSONの数値でも文字列のときと同じ結果を返す() {
        var result =
                simulate(
                        """
                        {"amount":100000,"paymentCount":12,"annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.totalFee").isEqualTo(8309);
        assertThat(result).bodyJson().extractingPath("$.firstPayment").isEqualTo(9034);
    }

    private MvcTestResult simulate(String json) {
        return mvc.post()
                .uri("/api/installment-simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}
