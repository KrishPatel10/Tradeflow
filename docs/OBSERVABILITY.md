# Observability

TradeFlow exports Prometheus-format metrics from `/actuator/prometheus`. The Docker Compose observability profile adds Prometheus with 15-day retention and a provisioned Grafana operations dashboard.

## Start locally

```bash
docker compose --profile app --profile observability up -d --build
```

- TradeFlow: `http://localhost:4200`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

Grafana defaults to `admin` / `tradeflow-local` for local development. Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` in `.env` before using this profile in a shared environment.

The **TradeFlow / TradeFlow Operations** dashboard is provisioned automatically and tracks:

- transfer throughput, outcome, and p50/p95 latency;
- pending and dead transactional outbox events;
- Kafka publication outcomes and consumer lag;
- Redis fail-open activity;
- backend scrape health.

## Alert rules

Prometheus loads `observability/prometheus/rules/tradeflow-alerts.yml`. Rules cover backend availability, unexpected transfer errors, outbox backlog/dead events, Redis fallback, and Kafka consumer lag.

The local profile intentionally has no Alertmanager receiver, so alerts are visible in Prometheus but do not page anyone. A deployed environment should route critical alerts to its incident system and warning alerts to the owning team.

## Security and retention

The Prometheus endpoint is unauthenticated so the in-cluster scraper can read it. Do not expose the backend management path, Prometheus, or Grafana directly to the public internet. Place them on a private network or behind authenticated infrastructure controls. Adjust retention and persistent volume sizing to the environment's traffic and compliance needs.
