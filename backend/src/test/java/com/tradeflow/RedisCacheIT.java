package com.tradeflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.tradeflow.account.dto.CreateAccountRequest;
import com.tradeflow.account.service.AccountService;
import com.tradeflow.funding.dto.FundAccountRequest;
import com.tradeflow.funding.service.AccountFundingService;
import com.tradeflow.transfer.dto.CreateTransferRequest;
import com.tradeflow.transfer.service.TransferService;
import com.tradeflow.user.dto.CreateUserRequest;
import com.tradeflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = "tradeflow.kafka.publisher-enabled=false")
class RedisCacheIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("tradeflow.cache.enabled", () -> true);
    }

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountFundingService accountFundingService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void accountReadsAreCachedTransfersEvictAfterCommitAndRedisFailureIsNonFatal() {
        var user = userService.create(new CreateUserRequest("redis@example.com", "Cache", "Owner",
                "TradeFlow123!"));
        authenticate(user.id());
        var source = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var destination = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        authenticate(user.id(), "ADMIN");
        accountFundingService.fund(source.id(), new FundAccountRequest(new BigDecimal("100.00"), "INR", "Test seed"),
                "redis-seed");
        authenticate(user.id());

        accountService.get(source.id());
        var sourceKey = AccountService.accountCacheKey(source.id());
        assertThat(redisTemplate.hasKey(sourceKey)).isTrue();

        transferService.create(new CreateTransferRequest(source.id(), destination.id(), new BigDecimal("20.00"),
                "INR"), "redis-invalidation");
        assertThat(redisTemplate.hasKey(sourceKey)).isFalse();
        assertThat(accountService.get(source.id()).balance()).isEqualByComparingTo("80.00");

        REDIS.stop();
        assertThat(accountService.get(destination.id()).balance()).isEqualByComparingTo("20.00");
    }

    private void authenticate(UUID userId) {
        authenticate(userId, "CUSTOMER");
    }

    private void authenticate(UUID userId, String role) {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
