package com.example.cardpayment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@Import(TestcontainersConfiguration.class)
class CardPaymentSimulatorApplicationTests {

    @Test
    void contextLoads() {}
}
