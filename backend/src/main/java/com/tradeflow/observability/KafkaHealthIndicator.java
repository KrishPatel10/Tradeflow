package com.tradeflow.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component("kafka")
@ConditionalOnProperty(name = "tradeflow.kafka.publisher-enabled", havingValue = "true")
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaHealthIndicator(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${tradeflow.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public Health health() {
        try {
            var partitions = kafkaTemplate.partitionsFor(topic).size();
            return Health.up().withDetail("topic", topic).withDetail("partitions", partitions).build();
        } catch (RuntimeException exception) {
            return Health.down(exception).withDetail("topic", topic).build();
        }
    }
}
