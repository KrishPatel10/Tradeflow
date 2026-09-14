package com.tradeflow.funding.service;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

import com.tradeflow.account.entity.AccountStatus;
import com.tradeflow.account.repository.AccountRepository;
import com.tradeflow.account.service.AccountService;
import com.tradeflow.audit.service.AuditService;
import com.tradeflow.cache.CacheAsideService;
import com.tradeflow.common.exception.ApiException;
import com.tradeflow.common.exception.BadRequestException;
import com.tradeflow.common.exception.BusinessRuleException;
import com.tradeflow.common.exception.ConflictException;
import com.tradeflow.common.exception.ResourceNotFoundException;
import com.tradeflow.funding.dto.AccountFundingResponse;
import com.tradeflow.funding.dto.FundAccountRequest;
import com.tradeflow.funding.entity.AccountFunding;
import com.tradeflow.funding.entity.FundingRequestStatus;
import com.tradeflow.funding.repository.AccountFundingRepository;
import com.tradeflow.funding.repository.FundingIdempotencyRecordRepository;
import com.tradeflow.funding.repository.FundingIdempotencyReservation;
import com.tradeflow.security.CurrentUser;
import com.tradeflow.transfer.service.RequestFingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountFundingService {

    private final AccountRepository accountRepository;
    private final AccountFundingRepository fundingRepository;
    private final FundingIdempotencyRecordRepository idempotencyRepository;
    private final FundingIdempotencyReservation idempotencyReservation;
    private final RequestFingerprint requestFingerprint;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final CacheAsideService cache;

    public AccountFundingService(AccountRepository accountRepository, AccountFundingRepository fundingRepository,
            FundingIdempotencyRecordRepository idempotencyRepository,
            FundingIdempotencyReservation idempotencyReservation, RequestFingerprint requestFingerprint,
            CurrentUser currentUser, AuditService auditService, CacheAsideService cache) {
        this.accountRepository = accountRepository;
        this.fundingRepository = fundingRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.idempotencyReservation = idempotencyReservation;
        this.requestFingerprint = requestFingerprint;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.cache = cache;
    }

    @Transactional
    public AccountFundingResponse fund(UUID accountId, FundAccountRequest request, String rawIdempotencyKey) {
        currentUser.requireAdmin();
        var idempotencyKey = rawIdempotencyKey.trim();
        try {
            return process(accountId, request, idempotencyKey);
        } catch (ApiException exception) {
            auditService.recordFailure("ACCOUNT_FUNDING_REQUEST", idempotencyKey, "ACCOUNT_FUNDING_FAILED",
                    "{\"accountId\":\"" + accountId + "\",\"reason\":\"" + exception.getCode() + "\"}",
                    currentUser.id());
            throw exception;
        }
    }

    private AccountFundingResponse process(UUID accountId, FundAccountRequest request, String idempotencyKey) {
        var amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_FUNDING_AMOUNT", "Funding amount must be greater than zero.");
        }
        var currency = normalizeCurrency(request.currency());
        var reference = request.reference().trim();
        var fingerprint = requestFingerprint.forFunding(accountId, amount.toPlainString(), currency, reference);

        var reserved = idempotencyReservation.reserve(idempotencyKey, fingerprint);
        var idempotencyRecord = idempotencyRepository.findByKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Reserved funding idempotency record was not found."));
        if (!idempotencyRecord.getRequestFingerprint().equals(fingerprint)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used for a different funding request.");
        }
        if (reserved == 0 && idempotencyRecord.getStatus() == FundingRequestStatus.COMPLETED) {
            return toResponse(idempotencyRecord.getFunding());
        }

        var account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found."));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("ACCOUNT_NOT_ACTIVE", "Only active accounts can be funded.");
        }
        if (!account.getCurrency().equals(currency)) {
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Funding currency must match the account currency.");
        }

        account.credit(amount);
        var funding = fundingRepository.save(AccountFunding.create(account, amount, currency, reference,
                idempotencyKey, fingerprint, currentUser.id()));
        idempotencyRecord.complete(funding);
        cache.evictAfterCommit(AccountService.accountCacheKey(accountId));
        auditService.record("ACCOUNT_FUNDING", funding.getId().toString(), "ACCOUNT_FUNDED", null,
                "{\"accountId\":\"" + accountId + "\",\"amount\":\"" + amount.toPlainString()
                        + "\",\"currency\":\"" + currency + "\"}",
                currentUser.id());
        return toResponse(funding);
    }

    private String normalizeCurrency(String rawCurrency) {
        var currency = rawCurrency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("INVALID_CURRENCY", "Currency must be a valid ISO 4217 code.");
        }
    }

    private AccountFundingResponse toResponse(AccountFunding funding) {
        return new AccountFundingResponse(funding.getId(), funding.getAccount().getId(), funding.getAmount(),
                funding.getCurrency(), funding.getReference(), funding.getActorId(), funding.getCreatedAt());
    }
}
