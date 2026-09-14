package com.tradeflow.transfer.service;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tradeflow.account.entity.Account;
import com.tradeflow.account.entity.AccountStatus;
import com.tradeflow.account.repository.AccountRepository;
import com.tradeflow.account.service.AccountService;
import com.tradeflow.common.api.PageResponse;
import com.tradeflow.common.exception.BadRequestException;
import com.tradeflow.common.exception.BusinessRuleException;
import com.tradeflow.common.exception.ResourceNotFoundException;
import com.tradeflow.transfer.dto.CreateTransferRequest;
import com.tradeflow.transfer.dto.TransferResponse;
import com.tradeflow.transfer.entity.Transfer;
import com.tradeflow.transfer.mapper.TransferMapper;
import com.tradeflow.transfer.repository.TransferRepository;
import com.tradeflow.transfer.repository.IdempotencyRecordRepository;
import com.tradeflow.transfer.repository.IdempotencyReservation;
import com.tradeflow.security.CurrentUser;
import com.tradeflow.audit.service.AuditService;
import com.tradeflow.common.exception.ConflictException;
import com.tradeflow.common.exception.ApiException;
import com.tradeflow.transfer.entity.IdempotencyStatus;
import com.tradeflow.cache.CacheAsideService;
import com.tradeflow.outbox.service.OutboxService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransferRepository transferRepository;
    private final CurrentUser currentUser;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final RequestFingerprint requestFingerprint;
    private final AuditService auditService;
    private final IdempotencyReservation idempotencyReservation;
    private final CacheAsideService cache;
    private final OutboxService outboxService;
    private final MeterRegistry meterRegistry;

    public TransferService(AccountRepository accountRepository, AccountService accountService,
            TransferRepository transferRepository, CurrentUser currentUser,
            IdempotencyRecordRepository idempotencyRecordRepository, RequestFingerprint requestFingerprint,
            AuditService auditService, IdempotencyReservation idempotencyReservation, CacheAsideService cache,
            OutboxService outboxService, MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.transferRepository = transferRepository;
        this.currentUser = currentUser;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.requestFingerprint = requestFingerprint;
        this.auditService = auditService;
        this.idempotencyReservation = idempotencyReservation;
        this.cache = cache;
        this.outboxService = outboxService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public TransferResponse create(CreateTransferRequest request, String idempotencyKey) {
        var sample = Timer.start(meterRegistry);
        try {
            var response = process(request, idempotencyKey);
            meterRegistry.counter("tradeflow.transfers", "result", "completed").increment();
            return response;
        } catch (ApiException exception) {
            meterRegistry.counter("tradeflow.transfers", "result", "rejected", "reason", exception.getCode())
                    .increment();
            auditService.recordFailure("TRANSFER_REQUEST", idempotencyKey, "TRANSFER_FAILED",
                    "{\"reason\":\"" + exception.getCode() + "\"}", currentUser.id());
            throw exception;
        } catch (RuntimeException exception) {
            meterRegistry.counter("tradeflow.transfers", "result", "error").increment();
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("tradeflow.transfer.duration"));
        }
    }

    private TransferResponse process(CreateTransferRequest request, String idempotencyKey) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("SAME_ACCOUNT_TRANSFER", "Source and destination accounts must differ.");
        }

        var amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        var currency = normalizeCurrency(request.currency());
        var fingerprint = requestFingerprint.forTransfer(request, amount.toPlainString(), currency);
        var reserved = idempotencyReservation.reserve(idempotencyKey, fingerprint);
        var idempotencyRecord = idempotencyRecordRepository.findByKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Reserved idempotency record was not found."));
        if (!idempotencyRecord.getRequestFingerprint().equals(fingerprint)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used for a different request.");
        }
        if (reserved == 0 && idempotencyRecord.getStatus() == IdempotencyStatus.COMPLETED) {
            meterRegistry.counter("tradeflow.idempotency.replays").increment();
            return TransferMapper.toResponse(idempotencyRecord.getTransfer());
        }
        var orderedIds = List.of(request.fromAccountId(), request.toAccountId()).stream().sorted().toList();
        var lockedAccounts = accountRepository.findAllByIdForUpdate(orderedIds);
        if (lockedAccounts.size() != 2) {
            throw new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Source or destination account not found.");
        }

        var accountsById = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        var source = accountsById.get(request.fromAccountId());
        var destination = accountsById.get(request.toAccountId());
        currentUser.requireSelfOrAdmin(source.getUser().getId());

        requireActive(source, "source");
        requireActive(destination, "destination");
        if (!source.getCurrency().equals(currency) || !destination.getCurrency().equals(currency)) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "Both accounts must use the requested transfer currency.");
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_FUNDS",
                    "The source account does not have sufficient funds.");
        }

        source.debit(amount);
        destination.credit(amount);
        var transfer = transferRepository.save(Transfer.completed(source, destination, amount, currency,
                idempotencyKey, fingerprint));
        idempotencyRecord.complete(transfer);
        outboxService.recordTransferCompleted(transfer, source.getUser().getId());
        cache.evictAfterCommit(AccountService.accountCacheKey(source.getId()),
                AccountService.accountCacheKey(destination.getId()));
        auditService.record("TRANSFER", transfer.getId().toString(), "TRANSFER_COMPLETED", null,
                "{\"status\":\"COMPLETED\"}", currentUser.id());
        return TransferMapper.toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id) {
        var transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found."));
        if (!currentUser.isAdmin()) {
            var userId = currentUser.id();
            var ownsSource = transfer.getFromAccount().getUser().getId().equals(userId);
            var ownsDestination = transfer.getToAccount().getUser().getId().equals(userId);
            if (!ownsSource && !ownsDestination) {
                currentUser.requireSelfOrAdmin(transfer.getFromAccount().getUser().getId());
            }
        }
        return TransferMapper.toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> getHistory(UUID accountId, int page, int size) {
        var account = accountService.findEntity(accountId);
        currentUser.requireSelfOrAdmin(account.getUser().getId());
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(transferRepository.findHistory(accountId, pageable), TransferMapper::toResponse);
    }

    private void requireActive(Account account, String role) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("ACCOUNT_NOT_ACTIVE", "The " + role + " account is not active.");
        }
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
}
