package com.tradeflow.funding.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountFundingResponse(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String reference,
        UUID actorId,
        Instant createdAt) {
}
