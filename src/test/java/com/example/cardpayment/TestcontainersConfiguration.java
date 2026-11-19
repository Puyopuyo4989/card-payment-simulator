package com.example.cardpayment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** テスト用の PostgreSQL を Testcontainers で起動する。版は compose.yaml と合わせる（ADR 0011）。 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // テスト全体で1つだけ持つ。テストの設定が違って Spring のコンテキストが別になっても、同じコンテナを使う
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6") {
                @Override
                public void stop() {
                    // Spring はコンテキストを閉じるときにコンテナも閉じる。
                    // ほかのコンテキストが使っていても止まってしまうので、ここでは止めない。
                    // 後片付けは JVM の終了時に Testcontainers（Ryuk）が行う
                }
            };

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
