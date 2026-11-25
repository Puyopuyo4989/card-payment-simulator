package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.service.CardApplicationService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 申込管理の API（contracts/card-application-api.md）。入力は文字列で受け、入力チェックは validator が行う。
 * 個人情報が出ないよう、ログは書かない（research.md R11-1）。
 */
@RestController
@RequestMapping("/api/card-applications")
public class CardApplicationController {

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
}
