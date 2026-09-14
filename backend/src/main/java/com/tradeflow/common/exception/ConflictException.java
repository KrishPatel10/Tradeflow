package com.tradeflow.common.exception;

import org.springframework.http.HttpStatus;

public final class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
