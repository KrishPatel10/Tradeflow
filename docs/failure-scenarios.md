# Failure Scenarios

The following scenarios will receive automated coverage as their phases are implemented:

- Insufficient funds roll back all financial changes.
- A failure after debit but before credit rolls back the entire transfer.
- Concurrent transfers cannot overspend an account.
- Reusing an idempotency key cannot create a second transfer.
- Redis unavailability cannot prevent a transfer.
- Kafka unavailability leaves a committed outbox event available for retry.
- Duplicate Kafka delivery cannot create a duplicate notification.
