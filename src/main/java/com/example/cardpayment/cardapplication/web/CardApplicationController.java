package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.service.CardApplicationDetail;
import com.example.cardpayment.cardapplication.service.CardApplicationService;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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

    private final CardApplicationInputValidator validator;
    private final CardApplicationService service;

    public CardApplicationController(
            CardApplicationInputValidator validator, CardApplicationService service) {
        this.validator = validator;
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

    /** 一覧（contracts 2）。status が省略か空なら「すべて」。 */
    @GetMapping
    public CardApplicationListResponse list(@RequestParam(required = false) String status) {
        List<CardApplicationSummaryResponse> items =
                service.list(parseStatusFilter(status)).stream()
                        .map(CardApplicationSummaryResponse::from)
                        .toList();
        return new CardApplicationListResponse(items);
    }

    /** 詳細と変更履歴（contracts 3）。ないときは 404。 */
    @GetMapping("/{applicationNumber}")
    public CardApplicationDetailResponse detail(@PathVariable String applicationNumber) {
        CardApplicationDetail detail = service.detail(applicationNumber);
        return CardApplicationDetailResponse.from(detail.application(), detail.history());
    }

    private static Optional<ApplicationStatus> parseStatusFilter(String status) {
        if (status == null || status.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                ApplicationStatus.parse(status)
                        .orElseThrow(() ->
                                new InputValidationException(
                                        List.of(new FieldError("status", STATUS_INVALID)))));
    }
}
