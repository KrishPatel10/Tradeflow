package com.tradeflow.account.mapper;

import com.tradeflow.account.dto.AccountResponse;
import com.tradeflow.account.entity.Account;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getUser().getId(), account.getAccountNumber(),
                account.getCurrency(), account.getBalance(), account.getStatus(), account.getVersion(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
