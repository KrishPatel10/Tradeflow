package com.tradeflow.outbox.repository;

import java.util.List;
import java.util.UUID;

import com.tradeflow.outbox.entity.OutboxEvent;
import com.tradeflow.outbox.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<OutboxEvent> lockPublishableBatch(@Param("batchSize") int batchSize);

    long countByStatus(OutboxStatus status);
}
