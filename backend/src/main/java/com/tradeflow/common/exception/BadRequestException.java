package com.tradeflow.common.exception;

import org.springframework.http.HttpStatus;

public final class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
