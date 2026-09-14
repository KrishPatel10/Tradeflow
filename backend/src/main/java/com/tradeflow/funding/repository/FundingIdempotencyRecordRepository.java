package com.tradeflow.funding.repository;

import java.util.Optional;

import com.tradeflow.funding.entity.FundingIdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundingIdempotencyRecordRepository extends JpaRepository<FundingIdempotencyRecord, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from FundingIdempotencyRecord record left join fetch record.funding "
            + "where record.idempotencyKey = :key")
    Optional<FundingIdempotencyRecord> findByKeyForUpdate(@Param("key") String key);
}
