package com.tradeflow.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tradeflow.account.entity.AccountStatus;

public record AccountResponse(
        UUID id,
        UUID userId,
        String accountNumber,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
