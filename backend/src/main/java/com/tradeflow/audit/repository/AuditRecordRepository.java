package com.tradeflow.audit.repository;

import java.util.UUID;

import com.tradeflow.audit.entity.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
}
