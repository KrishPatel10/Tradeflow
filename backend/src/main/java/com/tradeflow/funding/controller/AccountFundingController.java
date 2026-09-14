package com.tradeflow.funding.controller;

import java.net.URI;
import java.util.UUID;

import com.tradeflow.funding.dto.AccountFundingResponse;
import com.tradeflow.funding.dto.FundAccountRequest;
import com.tradeflow.funding.service.AccountFundingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountFundingController {

    private final AccountFundingService fundingService;

    public AccountFundingController(AccountFundingService fundingService) {
        this.fundingService = fundingService;
    }

    @PostMapping("/{accountId}/fundings")
    public ResponseEntity<AccountFundingResponse> fund(@PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody FundAccountRequest request) {
        var response = fundingService.fund(accountId, request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/admin/accounts/" + accountId + "/fundings/" + response.id()))
                .body(response);
    }
}
