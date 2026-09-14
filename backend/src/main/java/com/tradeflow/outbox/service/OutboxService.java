package com.tradeflow.outbox.service;

import java.util.UUID;

import com.tradeflow.outbox.entity.OutboxEvent;
import com.tradeflow.outbox.event.TransferCompletedEvent;
import com.tradeflow.outbox.repository.OutboxEventRepository;
import com.tradeflow.transfer.entity.Transfer;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void recordTransferCompleted(Transfer transfer, UUID userId) {
        var eventId = UUID.randomUUID();
        var event = new TransferCompletedEvent(eventId, transfer.getId(), userId,
                transfer.getFromAccount().getId(), transfer.getToAccount().getId(), transfer.getAmount(),
                transfer.getCurrency(), transfer.getCompletedAt());
        var payload = objectMapper.writeValueAsString(event);
        repository.save(OutboxEvent.pending(eventId, "TRANSFER", transfer.getId(), "TRANSFER_COMPLETED", payload));
    }
}
