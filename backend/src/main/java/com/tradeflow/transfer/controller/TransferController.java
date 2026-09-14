package com.tradeflow.transfer.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.tradeflow.common.api.PageResponse;
import com.tradeflow.transfer.dto.CreateTransferRequest;
import com.tradeflow.transfer.dto.TransferResponse;
import com.tradeflow.transfer.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        var response = transferService.create(request, idempotencyKey.trim());
        return ResponseEntity.created(URI.create("/api/transfers/" + response.id())).body(response);
    }

    @GetMapping("/transfers/{transferId}")
    public TransferResponse get(@PathVariable UUID transferId) {
        return transferService.get(transferId);
    }

    @GetMapping("/accounts/{accountId}/transfers")
    public PageResponse<TransferResponse> getHistory(@PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return transferService.getHistory(accountId, page, size);
    }
}
