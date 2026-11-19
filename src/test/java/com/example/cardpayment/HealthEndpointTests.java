package com.example.cardpayment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** デプロイ後の確認に使う /actuator/health が、DB を含めて UP を返す。 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthEndpointTests {

    @Autowired private MockMvcTester mvc;

    @Test
    void DBにつながっていればUPを返す() {
        var result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
    }
}
