package com.example.cardpayment.cardapplication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 状態の変更の入力チェック（data-model.md 4、contracts 4 のメッセージの一覧）。値はすべて架空のもの。 */
class StatusChangeInputValidatorTest {

    static final String TO_STATUS_REQUIRED = "変更後の状態を指定してください";
    static final String TO_STATUS_INVALID =
            "変更後の状態は APPLIED・UNDER_REVIEW・APPROVED・REJECTED のどれかを指定してください";
    static final String STAFF_NAME_REQUIRED = "担当者名を入力してください";
    static final String STAFF_NAME_TOO_LONG = "担当者名は50文字以内で入力してください";
    static final String REASON_REQUIRED = "否決の理由を入力してください";
    static final String REASON_TOO_LONG = "否決の理由は500文字以内で入力してください";
    static final String VERSION_INVALID = "画面を開き直してから、もう一度操作してください";

    private static final String STAFF = "テスト 担当A";
    private static final String REASON = "テスト用の否決理由";

    private final StatusChangeInputValidator validator = new StatusChangeInputValidator();

    @Test
    void 正しい入力を読む() {
        ValidStatusChange result =
                validator.validate(
                        new StatusChangeRequest("UNDER_REVIEW", "　 テスト 担当A ", null, " 3 "));

        assertThat(result.toStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(result.staffName()).isEqualTo(STAFF);
        assertThat(result.rejectionReason()).isNull();
        assertThat(result.version()).isEqualTo(3);
    }

    @Test
    void 否決のときは前後の空白を取り除いた理由を返す() {
        ValidStatusChange result =
                validator.validate(new StatusChangeRequest("REJECTED", STAFF, " テスト用の否決理由　", "1"));

        assertThat(result.toStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo(REASON);
    }

    // toStatus

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "　 "})
    void 変更後の状態の未入力(String toStatus) {
        assertThat(errorsOf(toStatus, STAFF, null, "0"))
                .containsExactly(new FieldError("toStatus", TO_STATUS_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "X"})
    void 変更後の状態の値の誤り(String toStatus) {
        assertThat(errorsOf(toStatus, STAFF, null, "0"))
                .containsExactly(new FieldError("toStatus", TO_STATUS_INVALID));
    }

    // staffName

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "　 "})
    void 担当者名の未入力(String staffName) {
        assertThat(errorsOf("UNDER_REVIEW", staffName, null, "0"))
                .containsExactly(new FieldError("staffName", STAFF_NAME_REQUIRED));
    }

    @Test
    void 担当者名は50文字まで() {
        assertThat(
                        validator
                                .validate(request("UNDER_REVIEW", "あ".repeat(50), null, "0"))
                                .staffName())
                .hasSize(50);
        assertThat(errorsOf("UNDER_REVIEW", "あ".repeat(51), null, "0"))
                .containsExactly(new FieldError("staffName", STAFF_NAME_TOO_LONG));
    }

    @Test
    void 担当者名の文字数はコードポイントで数える() {
        // 「𠮷」は UTF-16 では2つの char だが、1文字として数える
        String fifty = "𠮷".repeat(50);

        assertThat(validator.validate(request("UNDER_REVIEW", fifty, null, "0")).staffName())
                .isEqualTo(fifty);
    }

    // rejectionReason

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "　 "})
    void 否決のときの理由の未入力(String reason) {
        assertThat(errorsOf("REJECTED", STAFF, reason, "0"))
                .containsExactly(new FieldError("rejectionReason", REASON_REQUIRED));
    }

    @Test
    void 否決の理由は500文字まで() {
        assertThat(
                        validator
                                .validate(request("REJECTED", STAFF, "あ".repeat(500), "0"))
                                .rejectionReason())
                .hasSize(500);
        assertThat(errorsOf("REJECTED", STAFF, "あ".repeat(501), "0"))
                .containsExactly(new FieldError("rejectionReason", REASON_TOO_LONG));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "UNDER_REVIEW", "APPROVED"})
    void 否決でないときは理由を確かめず保存もしない(String toStatus) {
        ValidStatusChange result =
                validator.validate(request(toStatus, STAFF, "あ".repeat(501), "0"));

        assertThat(result.rejectionReason()).isNull();
    }

    // version

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "-1",
                "1.0",
                "abc",
                "１",
                "2147483648",
                "99999999999",
                "123456789012345678901"
            })
    void 版の誤り(String version) {
        assertThat(errorsOf("UNDER_REVIEW", STAFF, null, version))
                .containsExactly(new FieldError("version", VERSION_INVALID));
    }

    @Test
    void 版は0から通り先頭の0は読み飛ばす() {
        assertThat(validator.validate(request("UNDER_REVIEW", STAFF, null, "0")).version())
                .isZero();
        assertThat(validator.validate(request("UNDER_REVIEW", STAFF, null, "01")).version())
                .isEqualTo(1);
        assertThat(validator.validate(request("UNDER_REVIEW", STAFF, null, "2147483647")).version())
                .isEqualTo(Integer.MAX_VALUE);
    }

    // 並びと、理由を確かめない場合

    /** toStatus が誤りのときは理由を確かめないので、4項目が同時に誤りになることはない。 否決を指定し、ほかの3項目を誤りにしたときの並びを確かめる。 */
    @Test
    void 否決でほかの3項目が誤りなら項目の順に返す() {
        assertThat(errorsOf("REJECTED", "", "", "x"))
                .extracting(FieldError::field)
                .containsExactly("staffName", "rejectionReason", "version");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"rejected"})
    void 変更後の状態が誤りなら理由は確かめない(String toStatus) {
        assertThat(errorsOf(toStatus, "", "", "x"))
                .extracting(FieldError::field)
                .containsExactly("toStatus", "staffName", "version");
    }

    // 個人情報を出さない（research.md R11-1）

    @Test
    void メッセージに入力の値を入れない() {
        String longStaff = "テスト担当" + "あ".repeat(50);
        String longReason = "テスト理由" + "あ".repeat(500);

        InputValidationException exception =
                catchThrowableOfType(
                        InputValidationException.class,
                        () -> validator.validate(request("REJECTED", longStaff, longReason, "x")));

        assertThat(exception.getMessage()).doesNotContain("テスト担当", "テスト理由");
        assertThat(exception.errors())
                .extracting(FieldError::message)
                .allSatisfy(message -> assertThat(message).doesNotContain("テスト担当", "テスト理由"));
    }

    @Test
    void 確かめた結果のtoStringに担当者名と理由を出さない() {
        ValidStatusChange result = validator.validate(request("REJECTED", STAFF, REASON, "0"));

        assertThat(result.toString()).doesNotContain(STAFF, REASON).contains("REJECTED");
    }

    @Test
    void リクエストのtoStringに担当者名と理由を出さない() {
        assertThat(request("REJECTED", STAFF, REASON, "0").toString())
                .doesNotContain(STAFF, REASON);
    }

    private static StatusChangeRequest request(
            String toStatus, String staffName, String reason, String version) {
        return new StatusChangeRequest(toStatus, staffName, reason, version);
    }

    private List<FieldError> errorsOf(
            String toStatus, String staffName, String reason, String version) {
        return catchThrowableOfType(
                        InputValidationException.class,
                        () -> validator.validate(request(toStatus, staffName, reason, version)))
                .errors();
    }
}
