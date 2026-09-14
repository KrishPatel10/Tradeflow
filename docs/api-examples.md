# API request examples

Health check:

```http
GET /actuator/health
```

Expected response after PostgreSQL is healthy:

```json
{"status":"UP"}
```

Open a customer account. New accounts always have a `0.00` balance:

```http
POST /api/accounts
Authorization: Bearer <customer-access-token>
Content-Type: application/json

{
  "userId": "<customer-id>",
  "currency": "INR"
}
```

Fund that account through the administrator-only command:

```http
POST /api/admin/accounts/<account-id>/fundings
Authorization: Bearer <admin-access-token>
Idempotency-Key: deposit-2026-0001
Content-Type: application/json

{
  "amount": 1000.00,
  "currency": "INR",
  "reference": "verified-cash-deposit-0001"
}
```

Use a new idempotency key for each logical funding. Retrying the same request with the same key returns the original funding record; changing the payload for an existing key returns `409 IDEMPOTENCY_KEY_REUSED`.
