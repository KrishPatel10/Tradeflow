package com.tradeflow.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.tradeflow.user.entity.UserStatus;
import com.tradeflow.user.entity.UserRole;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
