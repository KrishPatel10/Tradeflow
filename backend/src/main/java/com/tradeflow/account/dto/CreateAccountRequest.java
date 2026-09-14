package com.tradeflow.account.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @NotNull UUID userId,
        @NotNull @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter currency code") String currency) {
}
