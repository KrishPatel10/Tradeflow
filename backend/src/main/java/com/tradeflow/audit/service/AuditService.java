package com.tradeflow.audit.service;

import java.util.UUID;

import com.tradeflow.audit.dto.AuditRecordResponse;
import com.tradeflow.audit.entity.AuditRecord;
import com.tradeflow.audit.repository.AuditRecordRepository;
import com.tradeflow.common.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRecordRepository repository;

    public AuditService(AuditRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String entityType, String entityId, String action, String previousState,
            String newState, UUID actorId) {
        repository.save(AuditRecord.create(entityType, entityId, action, previousState, newState, actorId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String entityType, String entityId, String action, String newState, UUID actorId) {
        repository.save(AuditRecord.create(entityType, entityId, action, null, newState, actorId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditRecordResponse> getAll(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(repository.findAll(pageable), this::toResponse);
    }

    private AuditRecordResponse toResponse(AuditRecord record) {
        return new AuditRecordResponse(record.getId(), record.getEntityType(), record.getEntityId(),
                record.getAction(), record.getPreviousState(), record.getNewState(), record.getActorId(),
                record.getCreatedAt());
    }
}
