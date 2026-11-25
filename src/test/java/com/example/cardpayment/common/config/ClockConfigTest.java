package com.example.cardpayment.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Clock の Bean は、サーバーの既定に頼らず、日本時間を明示する（research.md R10）。 */
class ClockConfigTest {

    @Test
    void ClockのタイムゾーンはAsiaTokyo() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }
}
