package com.example.cardpayment.cardapplication.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.repository.CardApplicationRepository;
import com.example.cardpayment.common.config.MutableTestClock;
import com.example.cardpayment.common.config.TestClockConfiguration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 2人の担当者が同時に同じ申込の状態を変えたとき（spec のエッジケース、FR-019）。 2つのスレッドが別々のトランザクションで動くので、テストを
 * {@code @Transactional} にしない。値はすべて架空のもの。
 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class ConcurrentStatusChangeTest {

    private static final String NUMBER = "A00000001";
    private static final String URI = "/api/card-applications/" + NUMBER + "/status-changes";

    private static final String APPROVE_BY_A =
            """
            {"toStatus":"APPROVED","staffName":"テスト 担当A","version":"1"}
            """;
    private static final String REJECT_BY_B =
            """
            {"toStatus":"REJECTED","staffName":"テスト 担当B","rejectionReason":"テスト用の否決理由","version":"1"}
            """;

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private MutableTestClock clock;
    @Autowired private CardApplicationRepository repository;

    @BeforeEach
    void setUp() {
        CardApplicationTables.clear(jdbcClient);
        clock.set(TestClockConfiguration.START);
        registerUnderReview();
    }

    @RepeatedTest(5)
    void 同じ版で同時に変えると片方だけが通りもう片方は409() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcTestResult> approve = executor.submit(() -> send(APPROVE_BY_A, ready, start));
            Future<MvcTestResult> reject = executor.submit(() -> send(REJECT_BY_B, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int approveStatus = approve.get(30, TimeUnit.SECONDS).getResponse().getStatus();
            int rejectStatus = reject.get(30, TimeUnit.SECONDS).getResponse().getStatus();

            assertThat(List.of(approveStatus, rejectStatus))
                    .containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
            MvcTestResult loser = approveStatus == 409 ? approve.get() : reject.get();
            CardApplicationApiTest.assertConflict(loser, URI);

            ApplicationStatus winner =
                    approveStatus == 200 ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED;
            CardApplication saved = repository.findByNumber(NUMBER).orElseThrow();
            assertThat(saved.status()).isEqualTo(winner);
            assertThat(saved.version()).isEqualTo(2);
            // 審査中にした1行と、通った方の1行だけ
            assertThat(repository.findHistory(NUMBER))
                    .extracting(entry -> entry.toStatus())
                    .containsExactly(ApplicationStatus.UNDER_REVIEW, winner);
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcTestResult send(String body, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return mvc.post().uri(URI).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    /** 1件登録し、審査中にする（version 1）。 */
    private void registerUnderReview() {
        var registered =
                mvc.post()
                        .uri("/api/card-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"nameKanji":"テスト 太郎","nameKana":"テスト　タロウ","birthDate":"1990-04-01",
                                 "annualIncome":"450","employerName":"テスト株式会社","phoneNumber":"0000000000"}
                                """)
                        .exchange();
        assertThat(registered).hasStatus(HttpStatus.CREATED);
        var reviewed =
                mvc.post()
                        .uri(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"toStatus":"UNDER_REVIEW","staffName":"テスト 担当C","version":"0"}
                                """)
                        .exchange();
        assertThat(reviewed).hasStatus(HttpStatus.OK);
    }
}
