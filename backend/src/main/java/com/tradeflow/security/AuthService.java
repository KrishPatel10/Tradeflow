package com.tradeflow.security;

import java.util.Locale;

import com.tradeflow.common.exception.ApiException;
import com.tradeflow.security.dto.LoginRequest;
import com.tradeflow.security.dto.TokenResponse;
import com.tradeflow.user.entity.User;
import com.tradeflow.user.entity.UserStatus;
import com.tradeflow.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        var user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireActive(user);
        return jwtTokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        try {
            var userId = jwtTokenService.validateRefreshToken(refreshToken);
            var user = userRepository.findById(userId).orElseThrow(this::invalidToken);
            requireActive(user);
            return jwtTokenService.issue(user);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException("USER_NOT_ACTIVE", "The user is not active.", HttpStatus.FORBIDDEN);
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException("INVALID_CREDENTIALS", "Email or password is incorrect.", HttpStatus.UNAUTHORIZED);
    }

    private ApiException invalidToken() {
        return new ApiException("INVALID_REFRESH_TOKEN", "The refresh token is invalid or expired.",
                HttpStatus.UNAUTHORIZED);
    }
}
