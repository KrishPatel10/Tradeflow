package com.tradeflow.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tradeflow.security.dto.TokenResponse;
import com.tradeflow.user.entity.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenService(JwtEncoder jwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            @Value("${tradeflow.security.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${tradeflow.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public TokenResponse issue(User user) {
        var accessToken = encode(user, "access", accessTokenTtl);
        var refreshToken = encode(user, "refresh", refreshTokenTtl);
        return new TokenResponse("Bearer", accessToken, accessTokenTtl.toSeconds(), refreshToken);
    }

    public UUID validateRefreshToken(String token) {
        return UUID.fromString(refreshJwtDecoder.decode(token).getSubject());
    }

    private String encode(User user, String tokenType, Duration ttl) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("tradeflow")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .claim("token_type", tokenType)
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
