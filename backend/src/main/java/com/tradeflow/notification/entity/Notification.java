package com.tradeflow.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID eventId, UUID userId, UUID transferId, String message) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.userId = userId;
        this.transferId = transferId;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public UUID getTransferId() { return transferId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
