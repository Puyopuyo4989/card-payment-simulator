package com.example.cardpayment.common.config;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * API のテストで、本番の Clock の代わりにテスト用の時計を使う。 @Import({TestcontainersConfiguration.class,
 * TestClockConfiguration.class}) で使い、各テストの前に START に戻す。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    /** 日本時間 2026-11-25 10:30。 */
    public static final Instant START = Instant.parse("2026-11-25T01:30:00Z");

    @Bean
    @Primary
    MutableTestClock testClock() {
        return new MutableTestClock(START);
    }
}
