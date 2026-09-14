package com.tradeflow.account.service;

import java.time.Duration;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

import com.tradeflow.account.dto.AccountResponse;
import com.tradeflow.account.dto.CreateAccountRequest;
import com.tradeflow.account.entity.Account;
import com.tradeflow.account.mapper.AccountMapper;
import com.tradeflow.account.repository.AccountRepository;
import com.tradeflow.common.api.PageResponse;
import com.tradeflow.common.exception.BadRequestException;
import com.tradeflow.common.exception.BusinessRuleException;
import com.tradeflow.common.exception.ResourceNotFoundException;
import com.tradeflow.user.entity.UserStatus;
import com.tradeflow.user.service.UserService;
import com.tradeflow.security.CurrentUser;
import com.tradeflow.audit.service.AuditService;
import com.tradeflow.cache.CacheAsideService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final UserService userService;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final CacheAsideService cache;
    private final Duration accountCacheTtl;

    public AccountService(AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator,
            UserService userService, CurrentUser currentUser, AuditService auditService, CacheAsideService cache,
            @Value("${tradeflow.cache.account-ttl:PT30S}") Duration accountCacheTtl) {
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.userService = userService;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.cache = cache;
        this.accountCacheTtl = accountCacheTtl;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        currentUser.requireSelfOrAdmin(request.userId());
        var user = userService.findEntity(request.userId());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("USER_NOT_ACTIVE", "Accounts can only be opened for active users.");
        }

        var currency = normalizeCurrency(request.currency());
        var account = Account.open(user, accountNumberGenerator.next(), currency);
        var saved = accountRepository.save(account);
        auditService.record("ACCOUNT", saved.getId().toString(), "ACCOUNT_CREATED", null,
                "{\"status\":\"ACTIVE\",\"currency\":\"" + currency + "\",\"balance\":\"0.00\"}",
                currentUser.id());
        return AccountMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        var cached = cache.get(accountCacheKey(id), AccountResponse.class);
        if (cached.isPresent()) {
            currentUser.requireSelfOrAdmin(cached.get().userId());
            return cached.get();
        }
        var account = findEntity(id);
        currentUser.requireSelfOrAdmin(account.getUser().getId());
        var response = AccountMapper.toResponse(account);
        cache.put(accountCacheKey(id), response, accountCacheTtl);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> getForUser(UUID userId, int page, int size) {
        currentUser.requireSelfOrAdmin(userId);
        userService.findEntity(userId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(accountRepository.findAllByUserId(userId, pageable), AccountMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> getAll(int page, int size) {
        currentUser.requireAdmin();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(accountRepository.findAll(pageable), AccountMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Account findEntity(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found."));
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

    public static String accountCacheKey(UUID id) {
        return "tradeflow:account:" + id;
    }
}
