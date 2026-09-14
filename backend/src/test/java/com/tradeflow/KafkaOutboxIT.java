package com.tradeflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tradeflow.account.dto.CreateAccountRequest;
import com.tradeflow.account.service.AccountService;
import com.tradeflow.funding.dto.FundAccountRequest;
import com.tradeflow.funding.service.AccountFundingService;
import com.tradeflow.notification.repository.NotificationRepository;
import com.tradeflow.notification.service.TransferNotificationConsumer;
import com.tradeflow.outbox.entity.OutboxStatus;
import com.tradeflow.outbox.repository.OutboxEventRepository;
import com.tradeflow.transfer.dto.CreateTransferRequest;
import com.tradeflow.transfer.service.TransferService;
import com.tradeflow.user.dto.CreateUserRequest;
import com.tradeflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class KafkaOutboxIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("tradeflow.kafka.publisher-enabled", () -> true);
        registry.add("tradeflow.kafka.publish-delay", () -> "100ms");
        registry.add("tradeflow.cache.enabled", () -> false);
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
    private OutboxEventRepository outboxEvents;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private TransferNotificationConsumer consumer;

    @Test
    void committedTransferIsPublishedAndConsumedExactlyOnce() throws Exception {
        var user = userService.create(new CreateUserRequest("kafka@example.com", "Event", "Owner",
                "TradeFlow123!"));
        authenticate(user.id());
        var source = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var destination = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        authenticate(user.id(), "ADMIN");
        accountFundingService.fund(source.id(), new FundAccountRequest(new BigDecimal("100.00"), "INR", "Test seed"),
                "kafka-seed");
        authenticate(user.id());

        transferService.create(new CreateTransferRequest(source.id(), destination.id(), new BigDecimal("25.00"),
                "INR"), "kafka-outbox");

        await(Duration.ofSeconds(30), () -> outboxEvents.countByStatus(OutboxStatus.PUBLISHED) == 1
                && notifications.count() == 1);
        var event = outboxEvents.findAll().getFirst();
        consumer.consume(event.getPayload());

        assertThat(notifications.count()).isOne();
        assertThat(event.getPublishedAt()).isNotNull();
    }

    private static void await(Duration timeout, CheckedCondition condition) throws Exception {
        var deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.isTrue()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Condition was not satisfied within " + timeout);
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

    @FunctionalInterface
    private interface CheckedCondition {
        boolean isTrue() throws Exception;
    }
}
