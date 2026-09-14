# TradeFlow Development Instructions

## Scope and delivery

- Read `PROJECT_PLAN.md` before making architectural changes.
- Work on only the requested phase and preserve existing working behavior.
- Do not introduce Redis, Kafka, notifications, or service extraction before their planned phase.
- Update documentation when setup, APIs, or behavior changes.

## Backend conventions

- Use Java 21, Spring Boot, Maven, PostgreSQL, and Flyway.
- Organize code by business feature under `com.tradeflow`.
- Keep controllers thin and business rules in services or domain objects.
- Use request and response DTOs; never expose JPA entities from an API.
- Use `BigDecimal` for money and explicit currency values.
- Enforce critical invariants in both application logic and database constraints.
- Treat PostgreSQL as the authoritative source for balances.
- Add or update automated tests for each behavior change.

## Frontend conventions

- Use Angular with standalone components, strict TypeScript, SCSS, and feature-based organization.
- Put singleton services and interceptors in `core`, reusable presentation code in `shared`, and business screens in `features`.
- The frontend may present balances but must never make authoritative financial decisions.
- Keep backend contracts represented by typed frontend models.

## Completion report

Report files changed, behavior implemented, design decisions, tests executed and results, remaining limitations, and the recommended next task.
