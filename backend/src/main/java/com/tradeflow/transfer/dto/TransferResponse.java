package com.tradeflow.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tradeflow.transfer.entity.TransferStatus;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String failureReason,
        Instant createdAt,
        Instant completedAt) {
}
