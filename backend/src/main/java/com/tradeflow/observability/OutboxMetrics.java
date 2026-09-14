package com.tradeflow.observability;

import com.tradeflow.outbox.entity.OutboxStatus;
import com.tradeflow.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements MeterBinder {

    private final OutboxEventRepository repository;

    public OutboxMetrics(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("tradeflow.outbox.events", repository,
                value -> value.countByStatus(OutboxStatus.PENDING))
                .tag("status", "pending")
                .description("Events waiting to be published")
                .register(registry);
        Gauge.builder("tradeflow.outbox.events", repository,
                value -> value.countByStatus(OutboxStatus.DEAD))
                .tag("status", "dead")
                .description("Events moved to the dead-letter state")
                .register(registry);
    }
}
