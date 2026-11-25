package com.example.cardpayment.cardapplication.repository;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 申込の表を読み書きする（data-model.md 1）。時刻は OffsetDateTime（UTC）にして渡し、読むときに Instant に戻す。 timestamptz
 * は時刻そのものを持つので、DB の接続のタイムゾーンでは値が変わらない（research.md R10）。
 */
@Repository
public class CardApplicationRepository {

    private static final String SELECT_COLUMNS =
            """
            SELECT application_number, name_kanji, name_kana, birth_date, annual_income, employer_name,
                   phone_number, status, applied_at, updated_at, version
              FROM card_application
            """;

    private final JdbcClient jdbcClient;

    public CardApplicationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 状態「申込」・version 0 で登録し、DB が割り当てた申込番号を返す（spec FR-004）。 */
    public String insert(NewCardApplication application, Instant now) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO card_application
                          (name_kanji, name_kana, birth_date, annual_income, employer_name,
                           phone_number, status, applied_at, updated_at)
                        VALUES (:nameKanji, :nameKana, :birthDate, :annualIncome, :employerName,
                                :phoneNumber, :status, :now, :now)
                        RETURNING application_number
                        """)
                .param("nameKanji", application.nameKanji())
                .param("nameKana", application.nameKana())
                .param("birthDate", application.birthDate())
                .param("annualIncome", application.annualIncome())
                .param("employerName", application.employerName())
                .param("phoneNumber", application.phoneNumber())
                .param("status", ApplicationStatus.APPLIED.name())
                .param("now", toDb(now))
                .query(String.class)
                .single();
    }

    public Optional<CardApplication> findByNumber(String applicationNumber) {
        return jdbcClient
                .sql(SELECT_COLUMNS + " WHERE application_number = :number")
                .param("number", applicationNumber)
                .query(CardApplicationRepository::toApplication)
                .optional();
    }

    private static CardApplication toApplication(ResultSet rs, int rowNum) throws SQLException {
        return new CardApplication(
                rs.getString("application_number"),
                rs.getString("name_kanji"),
                rs.getString("name_kana"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getBigDecimal("annual_income"),
                rs.getString("employer_name"),
                rs.getString("phone_number"),
                ApplicationStatus.valueOf(rs.getString("status")),
                fromDb(rs, "applied_at"),
                fromDb(rs, "updated_at"),
                rs.getInt("version"));
    }

    private static OffsetDateTime toDb(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
