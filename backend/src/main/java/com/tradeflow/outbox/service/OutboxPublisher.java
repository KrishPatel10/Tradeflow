package com.tradeflow.outbox.service;

import java.util.concurrent.TimeUnit;

import com.tradeflow.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "tradeflow.kafka.publisher-enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String topic;
    private final int maxAttempts;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry, @Value("${tradeflow.kafka.topic}") String topic,
            @Value("${tradeflow.kafka.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${tradeflow.kafka.publish-delay:2s}")
    @Transactional
    public void publishBatch() {
        for (var event : repository.lockPublishableBatch(50)) {
            try {
                kafkaTemplate.send(topic, event.getId().toString(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.published();
                meterRegistry.counter("tradeflow.outbox.publications", "result", "success").increment();
            } catch (Exception exception) {
                event.failed(exception.getMessage(), maxAttempts);
                meterRegistry.counter("tradeflow.outbox.publications", "result", "failure").increment();
                log.warn("Could not publish outbox event {}; it will be retried", event.getId(), exception);
            }
        }
    }
}
