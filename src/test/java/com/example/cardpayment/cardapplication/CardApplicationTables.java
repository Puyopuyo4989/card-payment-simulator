package com.example.cardpayment.cardapplication;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * DB を使うテストの片付け（research.md R6）。テスト用の PostgreSQL は JVM 全体で共有しているので、 DB
 * を使うテストのクラスは、各テストの前（@BeforeEach）にこれを呼び、申込の2つの表を空にする。
 *
 * <p>RESTART IDENTITY で申込番号の連番も1に戻すので、テストの申込番号は毎回 A00000001 から始まる。 Flyway の履歴の表
 * （flyway_schema_history）は消さない。テストを並列にするときは、この方法を見直す。
 */
public final class CardApplicationTables {

    private CardApplicationTables() {}

    public static void clear(JdbcClient jdbcClient) {
        jdbcClient
                .sql("TRUNCATE card_application_status_history, card_application RESTART IDENTITY")
                .update();
    }
}
