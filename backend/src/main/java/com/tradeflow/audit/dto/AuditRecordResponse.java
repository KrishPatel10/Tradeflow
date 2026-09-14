package com.tradeflow.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditRecordResponse(
        UUID id,
        String entityType,
        String entityId,
        String action,
        String previousState,
        String newState,
        UUID actorId,
        Instant createdAt) {
}
