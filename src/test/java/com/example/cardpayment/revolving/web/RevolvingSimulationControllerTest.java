package com.example.cardpayment.revolving.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void 入力エラーは項目ごとのエラーをProblemDetailsで返す() {
        // spec US3 の7
        var result =
                simulate(
                        """
                        {"balance":"","monthlyPayment":"1500","annualRate":"12.25"}
                        """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("about:blank");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("入力に誤りがあります");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.instance")
                .isEqualTo("/api/revolving-simulations");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("balance", "monthlyPayment", "annualRate");
        // 誤った計算結果を返さない（SC-004）
        assertThat(result).bodyJson().doesNotHavePath("$.paymentCount");
        assertThat(result).bodyJson().doesNotHavePath("$.totalFee");
    }

    @Test
    void 毎月の支払額が1回目の手数料以下なら毎月の支払額のエラーを返す() {
        // spec US3 の4
        var result =
                simulate(
                        """
                        {"balance":"10000000","monthlyPayment":"125000","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors").asArray().hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("monthlyPayment");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください");
        assertThat(result).bodyJson().doesNotHavePath("$.paymentCount");
    }

    @Test
    void 完済までの回数が360回を超えるなら毎月の支払額のエラーを返す() {
        // spec US3 の5（390回かかる）
        var result =
                simulate(
                        """
                        {"balance":"10000000","monthlyPayment":"126000","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("monthlyPayment");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("完済までの回数が360回を超えます。毎月の支払額を増やしてください");
    }

    @Test
    void 利用残高の上限でも払い終わる支払額なら結果を返す() {
        // spec US3 の6
        var result =
                simulate(
                        """
                        {"balance":"10000000","monthlyPayment":"127000","annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.paymentCount").isEqualTo(335);
    }

    @Test
    void 完済までちょうど360回なら結果を返し361回以上ならエラーを返す() {
        // 人が別途確かめた値（tasks.md T001）
        var exactly360 =
                simulate(
                        """
                        {"balance":"949000","monthlyPayment":"12000","annualRate":"15.0"}
                        """);
        var over360 =
                simulate(
                        """
                        {"balance":"950000","monthlyPayment":"12000","annualRate":"15.0"}
                        """);

        assertThat(exactly360).hasStatus(HttpStatus.OK);
        assertThat(exactly360).bodyJson().extractingPath("$.paymentCount").isEqualTo(360);
        assertThat(over360).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(over360)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .isEqualTo("完済までの回数が360回を超えます。毎月の支払額を増やしてください");
    }

    @Test
    void ほかの項目が誤っていれば1回目の手数料以下と360回を超えるのエラーを返さない() {
        // contracts「この契約で確かめるテスト」の400。支払額だけ見れば、それぞれ US3 の4・tasks.md T001 の950,000円で払い終わらない条件
        var feeNotCovered =
                simulate(
                        """
                        {"balance":"10000000","monthlyPayment":"125000","annualRate":"abc"}
                        """);
        var tooManyPayments =
                simulate(
                        """
                        {"balance":"950000","monthlyPayment":"12000","annualRate":"abc"}
                        """);

        for (MvcTestResult result : List.of(feeNotCovered, tooManyPayments)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.errors").asArray().hasSize(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[0].field")
                    .isEqualTo("annualRate");
        }
    }

    @Test
    void 本文がJSONとして読めなければerrorsのない400を返す() {
        var result = simulate("{");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("リクエストの形式が正しくありません");
        assertThat(result).bodyJson().doesNotHavePath("$.errors");
    }

    @Test
    void 項目がJSONの数値でも文字列と同じ結果を返す() {
        var result =
                simulate(
                        """
                        {"balance":100000,"monthlyPayment":10000,"annualRate":"15.0"}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.paymentCount").isEqualTo(11);
        assertThat(result).bodyJson().extractingPath("$.totalFee").isEqualTo(7497);
    }

    private MvcTestResult simulate(String body) {
        return mvc.post()
                .uri("/api/revolving-simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
