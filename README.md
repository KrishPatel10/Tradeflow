# TradeFlow

TradeFlow is a portfolio-quality banking transaction platform for reliable transfers between customer accounts. The working stack includes an Angular customer application, a secured Spring Boot modular monolith, PostgreSQL, Redis, Kafka, and an idempotent notification consumer.

## Repository layout

```text
backend/          Spring Boot API and domain modules
frontend/         Angular application
docs/             Architecture and API documentation
docker-compose.yml  Local infrastructure
```

## Prerequisites

- Java 21
- Docker Desktop with Docker Compose
- Node.js 22 and Corepack

The backend includes the Maven wrapper, so a global Maven installation is not required.

## Start the full stack

```bash
docker compose --profile app up --build -d
```

Open `http://localhost:4200`. Customers see the transfer workspace; provisioned administrators see the operations console. See [deployment guidance](docs/DEPLOYMENT.md), [API examples](docs/API.md), [continuous delivery](docs/CI.md), and the [remaining delivery queue](docs/ROADMAP.md).

Start the optional local metrics stack with `docker compose --profile app --profile observability up -d --build`, then open Grafana at `http://localhost:3001`. See the [observability runbook](docs/OBSERVABILITY.md).

For faster local development, start only the infrastructure with `docker compose up -d`, then run the backend and frontend separately.

## Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

On Windows PowerShell, use `./mvnw.cmd spring-boot:run`. The API listens on `http://localhost:8080`; health is available at `http://localhost:8080/actuator/health`.

To build and run the backend using Docker instead of a local JDK:

```bash
docker compose --profile app up --build
```

## Run the frontend

```bash
corepack prepare pnpm@10.15.1 --activate
pnpm install
pnpm frontend:start
```

The Angular development server listens on `http://localhost:4200` and proxies `/api` and `/actuator` requests to the backend.

## Tests

```bash
cd backend
./mvnw test
cd ../
pnpm frontend:test
```

Install Chromium once, then run the browser suite against an existing stack:

```bash
pnpm e2e:install
pnpm e2e
```

For the complete journey—including test-only administrator funding, a lost-response retry, an atomic transfer, and Kafka notification delivery—run an isolated disposable stack:

```bash
pnpm e2e:stack
```

The isolated runner uses ports `14200`, `18080`, `15432`, `16380`, and `19092` by default, then removes its containers and volumes. Override them with the `TRADEFLOW_E2E_*_PORT` environment variables when needed.

## Current status

The foundation, reliable transfers, security/idempotency/audit, Redis caching, Kafka outbox notifications, administrator operations, browser journeys, CI/CD, local operational readiness, and Prometheus/Grafana observability are implemented. Customer accounts open at zero balance; money enters through the idempotent, audited administrator console. Notification extraction stays optional. See [PROJECT_PLAN.md](PROJECT_PLAN.md) for phase status.
