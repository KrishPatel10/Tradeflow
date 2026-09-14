package com.tradeflow.funding.repository;

import java.util.UUID;

import com.tradeflow.funding.entity.AccountFunding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountFundingRepository extends JpaRepository<AccountFunding, UUID> {
}
