package com.example.cardpayment.common.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * テスト用の時計。時刻を決めたり進めたりできる。タイムゾーンはわざと UTC にして、 本番のコードが Clock
 * のタイムゾーンに頼らず日本時間を使っていることも、一緒に確かめる（research.md R10）。
 */
public class MutableTestClock extends Clock {

    private volatile Instant instant;

    public MutableTestClock(Instant instant) {
        this.instant = instant;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("テスト用の時計はタイムゾーンを変えない");
    }
}
