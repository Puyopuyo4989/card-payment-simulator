package com.example.cardpayment.cardapplication.service;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.cardapplication.domain.StatusChange;
import com.example.cardpayment.cardapplication.repository.CardApplicationRepository;
import com.example.cardpayment.common.time.BusinessTime;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
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

    /** 一覧（spec FR-007・FR-008）。状態を指定しないときは、すべての申込。 */
    @Transactional(readOnly = true)
    public List<CardApplication> list(Optional<ApplicationStatus> status) {
        return repository.findAll(status);
    }

    /** 詳細と変更履歴（spec FR-010）。ないときは ApplicationNotFoundException。 */
    @Transactional(readOnly = true)
    public CardApplicationDetail detail(String applicationNumber) {
        CardApplication application =
                repository
                        .findByNumber(applicationNumber)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationNumber));
        return new CardApplicationDetail(application, repository.findHistory(applicationNumber));
    }

    /**
     * 状態を変え、履歴に1行足す（data-model.md 4 の 3〜6）。申込がない → 404、版が違う → 409、 許されない遷移 →
     * StatusTransitionException、保存の時点で0行 → 409（トランザクションを戻し、履歴を足さない）。
     */
    @Transactional
    public CardApplicationDetail changeStatus(
            String applicationNumber,
            ApplicationStatus to,
            String staffName,
            String rejectionReason,
            int version) {
        CardApplication current =
                repository
                        .findByNumber(applicationNumber)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationNumber));
        if (current.version() != version) {
            throw new StatusConflictException(applicationNumber);
        }
        StatusChange change =
                current.changeStatus(to, staffName, rejectionReason, BusinessTime.now(clock));
        if (repository.updateStatus(
                        applicationNumber, to, change.application().updatedAt(), version)
                == 0) {
            throw new StatusConflictException(applicationNumber);
        }
        repository.insertHistory(applicationNumber, change.history());
        return detail(applicationNumber);
    }
}
