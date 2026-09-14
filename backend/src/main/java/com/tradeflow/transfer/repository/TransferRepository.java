package com.tradeflow.transfer.repository;

import java.util.UUID;

import com.tradeflow.transfer.entity.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query("""
            select transfer from Transfer transfer
            where transfer.fromAccount.id = :accountId or transfer.toAccount.id = :accountId
            """)
    Page<Transfer> findHistory(@Param("accountId") UUID accountId, Pageable pageable);
}
