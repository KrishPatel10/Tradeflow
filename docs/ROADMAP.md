# Remaining delivery queue

TradeFlow's core financial and event-driven phases are implemented. Remaining work is ordered by product value and operational risk:

1. **Administrator operations:** secure first-admin bootstrap, account inventory, controlled funding, and audit review. Implemented.
2. **Browser end-to-end tests:** cover customer registration, zero-balance account creation, administrator funding, lost-response transfer retry, and notification delivery with Playwright. Implemented with a disposable isolated-stack runner.
3. **Continuous delivery:** run backend, Testcontainers, Angular, and browser tests on every change; build, scan, and publish immutable commit-SHA images. Implemented for GitHub Actions/GHCR.
4. **Production observability:** publish Prometheus metrics and provide Grafana dashboards and alerts for transfers, outbox backlog, Kafka lag, Redis fallback, and dependency health. Implemented; external Alertmanager routing remains environment-specific.
5. **Notification service extraction:** optional. Extract only when independent deployment or scaling is justified; the Kafka boundary already supports it.

All high-value platform milestones are implemented. Notification extraction remains an optional architecture step, not required unfinished work.
