# TradeFlow Project Plan

TradeFlow is a portfolio-quality banking transaction platform for reliable transfers between customer accounts. It starts as a modular monolith and evolves into an event-driven system without distributing the core balance transaction.

## Correctness guarantees

- Source debit, destination credit, and the transfer record commit or roll back together.
- A committed balance never becomes negative.
- Concurrent requests cannot overspend an account.
- A logical request cannot debit an account twice.
- Cached data is never authoritative for a balance decision.
- Events describe only committed business operations.

## Technology

- Backend: Java 21, Spring Boot, Maven, Spring Web MVC, Spring Data JPA, Bean Validation, Flyway
- Frontend: Angular, TypeScript, standalone components, SCSS
- Data and infrastructure: PostgreSQL, then Redis and Kafka in later phases
- Testing: JUnit, Spring Boot Test, Testcontainers, Vitest, and Playwright
- Operations: Actuator, Micrometer, structured logs, with Prometheus and Grafana as later enhancements

## Delivery phases

1. **Foundation:** backend and frontend scaffolds, PostgreSQL Compose service, profiles, Flyway, Actuator, logging, tests, and documentation.
2. **Reliable modular monolith:** users, accounts, pessimistically locked transfers, structured errors, pagination, and concurrency tests.
3. **Security, idempotency, and audit:** password hashing, JWT authentication, authorization, request fingerprints, safe retries, and audit records.
4. **Redis and performance:** cache-aside reads, after-commit invalidation, indexes, query inspection, and cache metrics. Redis must not approve transfers.
5. **Kafka and notifications:** transactional outbox, retrying publisher, idempotent notification consumer, and dead-letter handling.
6. **Optional extraction:** extract notifications while keeping users, accounts, balances, transfers, idempotency, and outbox in the core service.
7. **Operational readiness:** correlation IDs, structured logs, metrics, dependency-aware health checks, and deployment documentation.

Complete and test each phase before beginning the next. Priority order: correctness, testability, clarity, performance, then architectural complexity.

## Implementation status

- Foundation: complete
- Reliable modular monolith: complete and concurrency-tested on PostgreSQL
- Security, idempotency, and audit: complete
- Redis and performance: complete, including fail-open and after-commit invalidation tests
- Kafka and notifications: complete, including transactional outbox and idempotent-consumer tests
- Optional extraction: deferred by design; the notification module communicates through Kafka and is ready to extract when independent deployment is justified
- Operational readiness: implemented for local/container deployment with correlation IDs, metrics, dependency health, and runbooks; production infrastructure remains environment-specific
- Administrator operations: complete, including secure bootstrap, protected inventory, audited funding, and an Angular operations console
- Browser journeys: complete, including disposable infrastructure, administrator funding, lost-response idempotency, atomic balance verification, and Kafka notification delivery
- Continuous delivery: complete for GitHub Actions, including unit/integration/browser gates, immutable image builds, vulnerability scans, and GHCR publication from `main`
- Production observability: complete, including the Prometheus endpoint, outbox gauges, provisioned Grafana dashboards, and actionable alert rules
- Remaining delivery queue: optional notification extraction only when independent scaling or deployment is justified

## Key decisions

- IDs use UUIDs.
- Money uses `BigDecimal` and database decimal columns with explicit precision and scale.
- Both account rows are locked in ascending account-ID order during a transfer.
- Initial balances are always zero; funding is an explicit, idempotent administrator operation with its own audit record.
- Failed-attempt persistence must use a transaction boundary separate from a rolled-back money movement.
- Angular talks to the REST API only; Redis and Kafka remain backend infrastructure.
