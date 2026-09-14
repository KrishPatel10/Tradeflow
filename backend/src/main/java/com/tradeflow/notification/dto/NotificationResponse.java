package com.tradeflow.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID eventId,
        UUID transferId,
        String message,
        Instant createdAt) {
}
