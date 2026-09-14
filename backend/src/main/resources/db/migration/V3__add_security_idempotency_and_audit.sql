ALTER TABLE app_users ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE app_users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE app_users ADD CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'));

UPDATE app_users SET password_hash = '{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG'
WHERE password_hash IS NULL;
ALTER TABLE app_users ALTER COLUMN password_hash SET NOT NULL;

ALTER TABLE transfers ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE transfers ADD COLUMN request_fingerprint VARCHAR(64);
UPDATE transfers
SET idempotency_key = 'legacy-' || CAST(id AS VARCHAR), request_fingerprint = REPEAT('0', 64)
WHERE idempotency_key IS NULL;
ALTER TABLE transfers ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE transfers ALTER COLUMN request_fingerprint SET NOT NULL;
ALTER TABLE transfers ADD CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key);

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    transfer_id UUID REFERENCES transfers(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    previous_state TEXT,
    new_state TEXT,
    actor_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_entity_created ON audit_records(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_created ON audit_records(created_at DESC);
