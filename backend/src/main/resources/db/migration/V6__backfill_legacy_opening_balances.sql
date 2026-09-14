INSERT INTO account_fundings (
    id,
    account_id,
    amount,
    currency,
    reference,
    idempotency_key,
    request_fingerprint,
    actor_id,
    created_at
)
SELECT
    account.id,
    account.id,
    account.balance,
    account.currency,
    'legacy-opening-balance',
    'legacy-opening-' || CAST(account.id AS VARCHAR),
    REPEAT('0', 64),
    account.user_id,
    account.created_at
FROM accounts account
WHERE account.balance > 0
  AND NOT EXISTS (
      SELECT 1 FROM account_fundings funding WHERE funding.account_id = account.id
  );

INSERT INTO funding_idempotency_records (
    idempotency_key,
    request_fingerprint,
    funding_id,
    status,
    created_at,
    completed_at
)
SELECT
    funding.idempotency_key,
    funding.request_fingerprint,
    funding.id,
    'COMPLETED',
    funding.created_at,
    funding.created_at
FROM account_fundings funding
WHERE funding.reference = 'legacy-opening-balance'
  AND NOT EXISTS (
      SELECT 1
      FROM funding_idempotency_records record
      WHERE record.idempotency_key = funding.idempotency_key
  );
