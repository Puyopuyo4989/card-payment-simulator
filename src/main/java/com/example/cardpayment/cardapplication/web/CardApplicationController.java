package com.example.cardpayment.cardapplication.web;

import static com.example.cardpayment.common.web.InputRules.strip;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.StatusTransitionException;
import com.example.cardpayment.cardapplication.repository.CardApplicationSearchResult;
import com.example.cardpayment.cardapplication.service.CardApplicationDetail;
import com.example.cardpayment.cardapplication.service.CardApplicationService;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 申込管理の API（contracts/card-application-api.md）。入力は文字列で受け、入力チェックは validator が行う。
 * 個人情報が出ないよう、ログは書かない（research.md R11-1）。
 */
@RestController
@RequestMapping("/api/card-applications")
public class CardApplicationController {

    private static final String STATUS_INVALID =
            "状態は APPLIED・UNDER_REVIEW・APPROVED・REJECTED のどれかを指定してください";
    private static final int KEYWORD_MAX_LENGTH = 50;
    private static final String KEYWORD_TOO_LONG = "キーワードは50文字以内で入力してください";
    private static final String PAGE_INVALID = "ページは1以上の半角数字で指定してください";

    /** 半角数字の9桁まで（int に収まる）。0 は parsePage で誤りにする。先頭の0は受け付ける（年収と同じ）。 */
    private static final Pattern PAGE_PATTERN = Pattern.compile("[0-9]{1,9}");

    private final CardApplicationInputValidator validator;
    private final StatusChangeInputValidator statusChangeValidator;
    private final CardApplicationService service;

    public CardApplicationController(
            CardApplicationInputValidator validator,
            StatusChangeInputValidator statusChangeValidator,
            CardApplicationService service) {
        this.validator = validator;
        this.statusChangeValidator = statusChangeValidator;
        this.service = service;
    }

    /** 申込を登録する（contracts 1）。201 と Location と、登録した申込の詳細を返す。 */
    @PostMapping
    public ResponseEntity<CardApplicationDetailResponse> register(
            @RequestBody CardApplicationRequest request) {
        CardApplication registered = service.register(validator.validate(request));
        return ResponseEntity.created(
                        URI.create("/api/card-applications/" + registered.applicationNumber()))
                .body(CardApplicationDetailResponse.from(registered));
    }

    /**
     * 一覧（contracts 2）。status が省略か空なら「すべて」。 【Should】keyword（部分一致）と page（1から、20件ずつ）で絞り込む（contracts
     * 6）。3つの誤りは、まとめて 400 で返す。
     */
    @GetMapping
    public CardApplicationListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String page) {
        List<FieldError> errors = new ArrayList<>();
        Optional<ApplicationStatus> statusFilter = parseStatusFilter(status, errors);
        Optional<String> keywordFilter = parseKeyword(keyword, errors);
        int pageNumber = parsePage(page, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }
        CardApplicationSearchResult result =
                service.search(statusFilter, keywordFilter, pageNumber);
        List<CardApplicationSummaryResponse> items =
                result.items().stream().map(CardApplicationSummaryResponse::from).toList();
        return new CardApplicationListResponse(
                items, result.page(), result.totalPages(), result.totalItems());
    }

    /** 詳細と変更履歴（contracts 3）。ないときは 404。 */
    @GetMapping("/{applicationNumber}")
    public CardApplicationDetailResponse detail(@PathVariable String applicationNumber) {
        CardApplicationDetail detail = service.detail(applicationNumber);
        return CardApplicationDetailResponse.from(detail.application(), detail.history());
    }

    /**
     * 状態を変える（contracts 4）。入力チェック（DB を読まない）→ サービス（404・409・遷移）の順。 許されない遷移は、理由に応じたメッセージの 400（field
     * toStatus）にする。
     */
    @PostMapping("/{applicationNumber}/status-changes")
    public CardApplicationDetailResponse changeStatus(
            @PathVariable String applicationNumber, @RequestBody StatusChangeRequest request) {
        ValidStatusChange input = statusChangeValidator.validate(request);
        CardApplicationDetail detail;
        try {
            detail =
                    service.changeStatus(
                            applicationNumber,
                            input.toStatus(),
                            input.staffName(),
                            input.rejectionReason(),
                            input.version());
        } catch (StatusTransitionException e) {
            throw new InputValidationException(
                    List.of(new FieldError("toStatus", transitionMessage(e.reason()))));
        }
        return CardApplicationDetailResponse.from(detail.application(), detail.history());
    }

    private static String transitionMessage(StatusTransitionException.Reason reason) {
        return switch (reason) {
            case REVIEW_REQUIRED_FIRST -> "承認・否決の前に、先に「審査中」にしてください";
            case ALREADY_DECIDED -> "審査が終わった申込の状態は変えられません";
            case NOT_ALLOWED -> "この状態には変えられません。変えられるのは「申込→審査中」「審査中→承認」「審査中→否決」だけです";
        };
    }

    private static Optional<ApplicationStatus> parseStatusFilter(
            String status, List<FieldError> errors) {
        if (status == null || status.isEmpty()) {
            return Optional.empty();
        }
        Optional<ApplicationStatus> parsed = ApplicationStatus.parse(status);
        if (parsed.isEmpty()) {
            errors.add(new FieldError("status", STATUS_INVALID));
        }
        return parsed;
    }

    /** 【Should】前後の空白（全角も）を取り除き、空なら絞り込まない。50文字（コードポイント）まで。 */
    private static Optional<String> parseKeyword(String keyword, List<FieldError> errors) {
        String stripped = keyword == null ? "" : strip(keyword);
        if (stripped.isEmpty()) {
            return Optional.empty();
        }
        if (stripped.codePointCount(0, stripped.length()) > KEYWORD_MAX_LENGTH) {
            errors.add(new FieldError("keyword", KEYWORD_TOO_LONG));
            return Optional.empty();
        }
        return Optional.of(stripped);
    }

    /** 【Should】省略（空も）は1。1以上の半角数字だけを受け付ける（大きすぎる数も誤り）。 */
    private static int parsePage(String page, List<FieldError> errors) {
        if (page == null || page.isEmpty()) {
            return 1;
        }
        if (!PAGE_PATTERN.matcher(page).matches() || Integer.parseInt(page) < 1) {
            errors.add(new FieldError("page", PAGE_INVALID));
            return 1;
        }
        return Integer.parseInt(page);
    }
}
