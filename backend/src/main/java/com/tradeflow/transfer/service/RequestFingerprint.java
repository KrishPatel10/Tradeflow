package com.tradeflow.transfer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.tradeflow.transfer.dto.CreateTransferRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestFingerprint {

    public String forTransfer(CreateTransferRequest request, String normalizedAmount, String normalizedCurrency) {
        var canonical = request.fromAccountId() + "|" + request.toAccountId() + "|" + normalizedAmount + "|"
                + normalizedCurrency;
        return sha256(canonical);
    }

    public String forFunding(java.util.UUID accountId, String normalizedAmount, String normalizedCurrency,
            String normalizedReference) {
        return sha256(accountId + "|" + normalizedAmount + "|" + normalizedCurrency + "|" + normalizedReference);
    }

    private String sha256(String canonical) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
