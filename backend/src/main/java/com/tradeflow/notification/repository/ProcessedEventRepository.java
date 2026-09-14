package com.tradeflow.notification.repository;

import java.util.UUID;

import com.tradeflow.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
