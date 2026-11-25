package com.example.cardpayment.cardapplication.repository;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.cardapplication.domain.StatusHistoryEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    /**
     * 版が expectedVersion のときだけ状態と更新日時を変え、版を1増やす（楽観ロック、research.md R4）。 更新した行の数を返す（0
     * なら、ほかの人が先に変えたか、申込がない）。
     */
    public int updateStatus(
            String applicationNumber, ApplicationStatus to, Instant now, int expectedVersion) {
        return jdbcClient
                .sql(
                        """
                        UPDATE card_application
                           SET status = :status, updated_at = :now, version = version + 1
                         WHERE application_number = :number AND version = :version
                        """)
                .param("status", to.name())
                .param("now", toDb(now))
                .param("number", applicationNumber)
                .param("version", expectedVersion)
                .update();
    }

    /** 履歴に1行足す（spec FR-017）。 */
    public void insertHistory(String applicationNumber, StatusHistoryEntry entry) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO card_application_status_history
                          (application_number, changed_at, from_status, to_status, staff_name, rejection_reason)
                        VALUES (:number, :changedAt, :from, :to, :staffName, :reason)
                        """)
                .param("number", applicationNumber)
                .param("changedAt", toDb(entry.changedAt()))
                .param("from", entry.fromStatus().name())
                .param("to", entry.toStatus().name())
                .param("staffName", entry.staffName())
                .param("reason", entry.rejectionReason())
                .update();
    }

    /** 一覧。申込日時の新しい順で、同じ日時なら申込番号の大きい順（research.md R10）。 状態を指定したときは、その状態の申込だけを返す（spec FR-008）。 */
    public List<CardApplication> findAll(Optional<ApplicationStatus> status) {
        String order = " ORDER BY applied_at DESC, application_number DESC";
        if (status.isEmpty()) {
            return jdbcClient
                    .sql(SELECT_COLUMNS + order)
                    .query(CardApplicationRepository::toApplication)
                    .list();
        }
        return jdbcClient
                .sql(SELECT_COLUMNS + " WHERE status = :status" + order)
                .param("status", status.get().name())
                .query(CardApplicationRepository::toApplication)
                .list();
    }

    /** 申込の変更履歴。変更日時の古い順で、同じ日時なら入れた順（spec FR-018）。 */
    public List<StatusHistoryEntry> findHistory(String applicationNumber) {
        return jdbcClient
                .sql(
                        """
                        SELECT changed_at, from_status, to_status, staff_name, rejection_reason
                          FROM card_application_status_history
                         WHERE application_number = :number
                         ORDER BY changed_at, id
                        """)
                .param("number", applicationNumber)
                .query((rs, rowNum) ->
                        new StatusHistoryEntry(
                                fromDb(rs, "changed_at"),
                                ApplicationStatus.valueOf(rs.getString("from_status")),
                                ApplicationStatus.valueOf(rs.getString("to_status")),
                                rs.getString("staff_name"),
                                rs.getString("rejection_reason")))
                .list();
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
