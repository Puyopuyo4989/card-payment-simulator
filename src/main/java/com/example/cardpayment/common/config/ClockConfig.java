package com.example.cardpayment.common.config;

import com.example.cardpayment.common.time.BusinessTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 「今」を取る Clock。タイムゾーンは日本時間を明示し、サーバーの既定に頼らない（research.md R10）。テストでは決めた時刻の Clock に差し替える。 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(BusinessTime.JAPAN);
    }
}
