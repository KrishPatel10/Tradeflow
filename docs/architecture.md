# Architecture

TradeFlow begins as a modular monolith. PostgreSQL owns all financial state; the Angular client accesses that state through REST APIs.

```mermaid
flowchart LR
    UI[Angular frontend] -->|REST| API[Spring Boot modular monolith]
    Admin[Trusted administrator] -->|Idempotent funding command| API
    API --> DB[(PostgreSQL)]
    API -. Phase 3 cache-aside reads .-> Redis[(Redis)]
    DB -. Phase 4 transactional outbox .-> Publisher[Outbox publisher]
    Publisher -.-> Kafka[Kafka]
    Kafka -.-> Notifications[Notification consumer]
```

Customer-created accounts always start at zero. Balance increases enter through an administrator-only funding transaction with its own idempotency record and audit trail. Funding and transfer boundaries remain inside PostgreSQL transactions with locked account rows. Redis is never used to approve a balance change, and Kafka publication occurs only after an outbox record has committed.
