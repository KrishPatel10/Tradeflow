package com.tradeflow.funding.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "funding_idempotency_records")
public class FundingIdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funding_id")
    private AccountFunding funding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundingRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected FundingIdempotencyRecord() {
    }

    public void complete(AccountFunding funding) {
        this.funding = funding;
        this.status = FundingRequestStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public String getRequestFingerprint() { return requestFingerprint; }
    public AccountFunding getFunding() { return funding; }
    public FundingRequestStatus getStatus() { return status; }
}
