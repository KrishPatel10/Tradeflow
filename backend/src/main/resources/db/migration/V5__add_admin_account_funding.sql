CREATE TABLE account_fundings (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_account_fundings_positive_amount CHECK (amount > 0),
    CONSTRAINT ck_account_fundings_currency_uppercase CHECK (currency = UPPER(currency))
);

CREATE TABLE funding_idempotency_records (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    funding_id UUID REFERENCES account_fundings(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_funding_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

CREATE INDEX idx_account_fundings_account_created
    ON account_fundings(account_id, created_at DESC);
