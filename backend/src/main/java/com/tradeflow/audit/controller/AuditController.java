package com.tradeflow.audit.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.tradeflow.audit.dto.AuditRecordResponse;
import com.tradeflow.audit.service.AuditService;
import com.tradeflow.common.api.PageResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/audit-records")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditRecordResponse> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.getAll(page, size);
    }
}
