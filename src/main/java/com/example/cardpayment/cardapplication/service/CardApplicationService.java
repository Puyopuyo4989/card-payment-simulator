package com.example.cardpayment.cardapplication.service;

import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.cardapplication.repository.CardApplicationRepository;
import com.example.cardpayment.common.time.BusinessTime;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 申込管理の処理の流れ（data-model.md 4）。トランザクションはここでまとめる。 */
@Service
public class CardApplicationService {

    private final CardApplicationRepository repository;
    private final Clock clock;

    public CardApplicationService(CardApplicationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** 申込を登録し、保存した内容を読み直して返す（spec FR-004）。 */
    @Transactional
    public CardApplication register(NewCardApplication application) {
        String applicationNumber = repository.insert(application, BusinessTime.now(clock));
        return repository.findByNumber(applicationNumber).orElseThrow();
    }
}
