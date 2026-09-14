package com.tradeflow.account.service;

import java.util.Locale;
import java.util.UUID;

import com.tradeflow.account.repository.AccountRepository;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String next() {
        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var candidate = "TF" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number.");
    }
}
