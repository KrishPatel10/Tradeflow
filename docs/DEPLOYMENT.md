# Deployment guide

## Local full stack

Copy `.env.example` to `.env`, replace `JWT_SECRET` with a Base64-encoded secret of at least 32 bytes, and optionally configure the first administrator:

```dotenv
TRADEFLOW_ADMIN_EMAIL=operations@example.com
TRADEFLOW_ADMIN_PASSWORD=<unique-password-with-at-least-12-characters>
```

Then run:

```powershell
docker compose --profile app up --build -d
```

Open `http://localhost:4200`. The browser sends `/api` requests to Nginx, which proxies them to the backend. PostgreSQL is the transactional source of truth; Redis is disposable read acceleration; Kafka carries only outbox events for already committed transfers.

The administrator is created only when the configured email does not exist. Startup fails rather than promoting an existing customer with the same email. Once created, the administrator remains in PostgreSQL and the bootstrap variables can be removed. Supplying a different password later does not rotate the existing credential.

## Production expectations

- Use managed PostgreSQL, Redis, and Kafka with encrypted connections and independently managed credentials.
- Provide bootstrap credentials through a secret manager, remove them after first provisioning, and use a separate credential-rotation process.
- Run at least two backend replicas. `FOR UPDATE SKIP LOCKED` allows their outbox publishers to cooperate.
- Terminate TLS at the load balancer and restrict `/actuator` to the operations network.
- Back up PostgreSQL and test restoration. Redis and Kafka are not substitutes for ledger backups.
- Alert on HTTP failure rate, transfer rejection/error counters, outbox pending/dead gauges, consumer lag, database saturation, and dependency health.
- Preserve the `X-Correlation-ID` response header in gateway and centralized logs.

## Failure behavior

- PostgreSQL unavailable: writes fail and health reports down.
- Redis unavailable: cached reads fall back to PostgreSQL; transfers continue.
- Kafka unavailable: committed transfers continue and their outbox rows remain pending for retry.
- Notification redelivery: the `processed_events` primary key makes consumption idempotent.
