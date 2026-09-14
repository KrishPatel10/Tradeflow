package com.tradeflow.notification.service;

import com.tradeflow.notification.entity.Notification;
import com.tradeflow.notification.entity.ProcessedEvent;
import com.tradeflow.notification.repository.NotificationRepository;
import com.tradeflow.notification.repository.ProcessedEventRepository;
import com.tradeflow.outbox.event.TransferCompletedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "tradeflow.kafka.publisher-enabled", havingValue = "true")
public class TransferNotificationConsumer {

    private static final String CONSUMER_NAME = "transfer-notification-v1";

    private final ProcessedEventRepository processedEvents;
    private final NotificationRepository notifications;
    private final ObjectMapper objectMapper;

    public TransferNotificationConsumer(ProcessedEventRepository processedEvents, NotificationRepository notifications,
            ObjectMapper objectMapper) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${tradeflow.kafka.topic}")
    @Transactional
    public void consume(String payload) {
        var event = objectMapper.readValue(payload, TransferCompletedEvent.class);
        if (processedEvents.existsById(event.eventId())) {
            return;
        }
        var message = "Transfer " + event.transferId() + " for " + event.amount().toPlainString() + " "
                + event.currency() + " completed.";
        notifications.save(new Notification(event.eventId(), event.userId(), event.transferId(), message));
        processedEvents.save(new ProcessedEvent(event.eventId(), CONSUMER_NAME));
    }
}
