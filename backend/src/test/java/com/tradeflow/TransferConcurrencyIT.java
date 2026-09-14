package com.tradeflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

import com.tradeflow.account.dto.CreateAccountRequest;
import com.tradeflow.account.service.AccountService;
import com.tradeflow.common.exception.BusinessRuleException;
import com.tradeflow.funding.dto.FundAccountRequest;
import com.tradeflow.funding.service.AccountFundingService;
import com.tradeflow.transfer.dto.CreateTransferRequest;
import com.tradeflow.transfer.repository.TransferRepository;
import com.tradeflow.transfer.service.TransferService;
import com.tradeflow.user.dto.CreateUserRequest;
import com.tradeflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = "tradeflow.kafka.publisher-enabled=false")
class TransferConcurrencyIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountFundingService accountFundingService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentTransfersCannotOverspendSourceAccount() throws Exception {
        var user = userService.create(new CreateUserRequest("concurrency@example.com", "Concurrent", "Owner",
                "TradeFlow123!"));
        authenticate(user.id());
        var source = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var destination = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        fund(user.id(), source.id(), "100.00", "concurrency-seed");

        var start = new CountDownLatch(1);
        var outcomes = new ArrayList<Boolean>();
        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var index = 0; index < 10; index++) {
                futures.add(executor.submit(() -> {
                    authenticate(user.id());
                    start.await();
                    try {
                        transferService.create(new CreateTransferRequest(source.id(), destination.id(),
                                new BigDecimal("20.00"), "INR"), "concurrent-" + UUID.randomUUID());
                        return true;
                    } catch (BusinessRuleException exception) {
                        assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_FUNDS");
                        return false;
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(outcomes).containsExactlyInAnyOrder(true, true, true, true, true,
                false, false, false, false, false);
        assertThat(accountService.get(source.id()).balance()).isEqualByComparingTo("0.00");
        assertThat(accountService.get(destination.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(transferRepository.findHistory(source.id(), Pageable.unpaged()).getTotalElements()).isEqualTo(5);
    }

    @Test
    void oppositeDirectionTransfersUseConsistentLockOrdering() throws Exception {
        var user = userService.create(new CreateUserRequest("deadlock@example.com", "Lock", "Order",
                "TradeFlow123!"));
        authenticate(user.id());
        var first = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var second = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        fund(user.id(), first.id(), "100.00", "opposite-first");
        fund(user.id(), second.id(), "100.00", "opposite-second");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                authenticate(user.id());
                start.await();
                return transferService.create(new CreateTransferRequest(first.id(), second.id(),
                        new BigDecimal("10.00"), "INR"), "opposite-one");
            });
            var two = executor.submit(() -> {
                authenticate(user.id());
                start.await();
                return transferService.create(new CreateTransferRequest(second.id(), first.id(),
                        new BigDecimal("10.00"), "INR"), "opposite-two");
            });
            start.countDown();
            assertThat(one.get(30, TimeUnit.SECONDS).status().name()).isEqualTo("COMPLETED");
            assertThat(two.get(30, TimeUnit.SECONDS).status().name()).isEqualTo("COMPLETED");
        }

        assertThat(accountService.get(first.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(accountService.get(second.id()).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void simultaneousRetriesWithTheSameIdempotencyKeyCreateOneTransfer() throws Exception {
        var user = userService.create(new CreateUserRequest("idempotency-race@example.com", "Retry", "Owner",
                "TradeFlow123!"));
        authenticate(user.id());
        var source = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var destination = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        fund(user.id(), source.id(), "100.00", "idempotency-seed");
        var request = new CreateTransferRequest(source.id(), destination.id(), new BigDecimal("20.00"), "INR");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                authenticate(user.id());
                start.await();
                return transferService.create(request, "same-key-at-the-same-time");
            });
            var second = executor.submit(() -> {
                authenticate(user.id());
                start.await();
                return transferService.create(request, "same-key-at-the-same-time");
            });
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS).id())
                    .isEqualTo(second.get(30, TimeUnit.SECONDS).id());
        }

        assertThat(accountService.get(source.id()).balance()).isEqualByComparingTo("80.00");
        assertThat(accountService.get(destination.id()).balance()).isEqualByComparingTo("20.00");
        assertThat(transferRepository.findHistory(source.id(), Pageable.unpaged()).getTotalElements()).isOne();
    }

    @Test
    void simultaneousFundingRetriesCreditTheAccountOnce() throws Exception {
        var user = userService.create(new CreateUserRequest("funding-race@example.com", "Funding", "Owner",
                "TradeFlow123!"));
        authenticate(user.id());
        var account = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var request = new FundAccountRequest(new BigDecimal("100.00"), "INR", "Concurrent deposit");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                authenticate(user.id(), "ADMIN");
                start.await();
                return accountFundingService.fund(account.id(), request, "same-funding-key");
            });
            var second = executor.submit(() -> {
                authenticate(user.id(), "ADMIN");
                start.await();
                return accountFundingService.fund(account.id(), request, "same-funding-key");
            });
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS).id())
                    .isEqualTo(second.get(30, TimeUnit.SECONDS).id());
        }

        authenticate(user.id());
        assertThat(accountService.get(account.id()).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void databaseFailureRollsBackBothBalanceChanges() {
        var user = userService.create(new CreateUserRequest("rollback@example.com", "Rollback", "Owner",
                "TradeFlow123!"));
        authenticate(user.id());
        var source = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        var destination = accountService.create(new CreateAccountRequest(user.id(), "INR"));
        fund(user.id(), source.id(), "100.00", "rollback-seed");
        jdbcTemplate.execute("ALTER TABLE transfers ADD CONSTRAINT test_reject_amount CHECK (amount < 50)");

        try {
            assertThatThrownBy(() -> transferService.create(new CreateTransferRequest(source.id(), destination.id(),
                    new BigDecimal("60.00"), "INR"), "rollback-one"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("ALTER TABLE transfers DROP CONSTRAINT test_reject_amount");
        }

        assertThat(accountService.get(source.id()).balance()).isEqualByComparingTo("100.00");
        assertThat(accountService.get(destination.id()).balance()).isEqualByComparingTo("0.00");
        assertThat(transferRepository.findHistory(source.id(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    private void authenticate(UUID userId) {
        authenticate(userId, "CUSTOMER");
    }

    private void fund(UUID actorId, UUID accountId, String amount, String key) {
        authenticate(actorId, "ADMIN");
        accountFundingService.fund(accountId, new FundAccountRequest(new BigDecimal(amount), "INR", "Test seed"),
                key);
        authenticate(actorId);
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
