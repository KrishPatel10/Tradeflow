package com.tradeflow.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldErrorDetail> details) {
}
