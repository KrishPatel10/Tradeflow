package com.tradeflow.transfer.repository;

import java.util.Optional;

import com.tradeflow.transfer.entity.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from IdempotencyRecord record left join fetch record.transfer where record.idempotencyKey = :key")
    Optional<IdempotencyRecord> findByKeyForUpdate(@Param("key") String key);
}
