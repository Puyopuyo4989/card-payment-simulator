package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import java.time.Instant;

/** 詳細の history[] の1行（contracts 3）。rejectionReason は否決の行だけにあり、ほかは null。 */
public record StatusHistoryResponse(
        Instant changedAt,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        String staffName,
        String rejectionReason) {}
