# API examples

All protected endpoints require `Authorization: Bearer <access-token>`. Every response includes `X-Correlation-ID`; callers may send their own safe identifier in that header.

1. Register with `POST /api/users` using `email`, `firstName`, `lastName`, and `password`.
2. Sign in with `POST /api/auth/login` using `email` and `password`.
3. Create a zero-balance account with `POST /api/accounts` using `userId` and `currency`.
4. Fund an account with admin credentials at `POST /api/admin/accounts/{accountId}/fundings`. Supply a unique `Idempotency-Key` header and a body containing `amount`, `currency`, and `reference`.
5. Create a transfer with `POST /api/transfers`. Supply a unique `Idempotency-Key` header and a body containing `fromAccountId`, `toAccountId`, `amount`, and `currency`.
6. Read activity from `GET /api/accounts/{accountId}/transfers` and notifications from `GET /api/users/{userId}/notifications`.

Administrator inventory and review endpoints:

- `GET /api/admin/users?page=0&size=20`
- `GET /api/admin/accounts?page=0&size=20`
- `GET /api/admin/audit-records?page=0&size=20`

Customer registration always creates a `CUSTOMER`; the first administrator is provisioned from deployment secrets. Account creation cannot set a balance. Each funding has its own persisted record and audit entry, and the account row is locked while its balance changes.

During the V6 upgrade, pre-existing non-zero opening balances are represented as `legacy-opening-balance` funding records. New balances can only enter through the administrator command.

Funding and transfer idempotency keys have separate namespaces. Reusing a key with the same request returns the original result without changing a balance again. Reusing it with a different request returns `409 IDEMPOTENCY_KEY_REUSED`.
