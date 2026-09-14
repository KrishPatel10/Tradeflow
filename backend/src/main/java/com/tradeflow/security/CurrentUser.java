package com.tradeflow.security;

import java.util.UUID;

import com.tradeflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException("UNAUTHENTICATED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(jwt.getSubject());
    }

    public boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ApiException("ACCESS_DENIED", "Administrator access is required.", HttpStatus.FORBIDDEN);
        }
    }

    public void requireSelfOrAdmin(UUID userId) {
        if (!isAdmin() && !id().equals(userId)) {
            throw new ApiException("ACCESS_DENIED", "You cannot access this resource.", HttpStatus.FORBIDDEN);
        }
    }
}
