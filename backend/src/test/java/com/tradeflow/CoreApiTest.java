package com.tradeflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.tradeflow.account.repository.AccountRepository;
import com.tradeflow.audit.repository.AuditRecordRepository;
import com.tradeflow.funding.repository.AccountFundingRepository;
import com.tradeflow.funding.repository.FundingIdempotencyRecordRepository;
import com.tradeflow.security.AdminProvisioningService;
import com.tradeflow.transfer.repository.IdempotencyRecordRepository;
import com.tradeflow.transfer.repository.TransferRepository;
import com.tradeflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreApiTest {

    private static final String PASSWORD = "TradeFlow123!";

    @Autowired MockMvc mockMvc;
    @Autowired TransferRepository transferRepository;
    @Autowired IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired AccountFundingRepository accountFundingRepository;
    @Autowired FundingIdempotencyRecordRepository fundingIdempotencyRecordRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AuditRecordRepository auditRecordRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdminProvisioningService adminProvisioningService;

    @BeforeEach
    void cleanDatabase() {
        idempotencyRecordRepository.deleteAll();
        transferRepository.deleteAll();
        fundingIdempotencyRecordRepository.deleteAll();
        accountFundingRepository.deleteAll();
        accountRepository.deleteAll();
        auditRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createsUsersAccountsAndIdempotentAtomicTransfers() throws Exception {
        var user = registerAndLogin("owner@example.com");
        var admin = registerAdminAndLogin("admin-owner@example.com");
        var sourceId = createAccount(user, "INR");
        var destinationId = createAccount(user, "INR");
        fundAccount(admin, sourceId, "100.00", "opening-source", "fund-source-001");
        fundAccount(admin, destinationId, "25.00", "opening-destination", "fund-destination-001");
        var request = transferRequest(sourceId, destinationId, "40.00");

        var first = mockMvc.perform(authenticated(post("/api/transfers"), user)
                        .header("Idempotency-Key", "transfer-001")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        var transferId = JsonPath.<String>read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(authenticated(post("/api/transfers"), user)
                        .header("Idempotency-Key", "transfer-001")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(transferId));
        mockMvc.perform(authenticated(get("/api/accounts/{id}", sourceId), user))
                .andExpect(jsonPath("$.balance").value(60.00));
        mockMvc.perform(authenticated(get("/api/accounts/{id}", destinationId), user))
                .andExpect(jsonPath("$.balance").value(65.00));
        mockMvc.perform(authenticated(get("/api/accounts/{id}/transfers", sourceId), user))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsBusinessFailuresConflictingRetriesAndUnauthorizedAccess() throws Exception {
        var owner = registerAndLogin("rules@example.com");
        var other = registerAndLogin("other@example.com");
        var admin = registerAdminAndLogin("admin-rules@example.com");
        var sourceId = createAccount(owner, "INR");
        var destinationId = createAccount(owner, "INR");
        fundAccount(admin, sourceId, "20.00", "rules-source", "fund-rules-source");
        fundAccount(admin, destinationId, "10.00", "rules-destination", "fund-rules-destination");

        mockMvc.perform(authenticated(post("/api/transfers"), owner)
                        .header("Idempotency-Key", "insufficient-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest(sourceId, destinationId, "25.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mockMvc.perform(authenticated(get("/api/accounts/{id}", sourceId), owner))
                .andExpect(jsonPath("$.balance").value(20.00));
        mockMvc.perform(authenticated(get("/api/accounts/{id}", sourceId), other))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accounts/{id}", sourceId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(authenticated(post("/api/transfers"), owner)
                        .header("Idempotency-Key", "conflict-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest(sourceId, destinationId, "5.00")))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticated(post("/api/transfers"), owner)
                        .header("Idempotency-Key", "conflict-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest(sourceId, destinationId, "6.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void hashesPasswordsIssuesRefreshTokensAndRejectsDuplicateEmail() throws Exception {
        var user = registerAndLogin("person@example.com");
        var stored = userRepository.findById(user.id()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getPasswordHash()).doesNotContain(PASSWORD);

        var login = login("person@example.com");
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(userRequest("PERSON@EXAMPLE.COM")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void accountsStartAtZeroAndOnlyAdminsCanFundThemIdempotently() throws Exception {
        var owner = registerAndLogin("funding-owner@example.com");
        var admin = registerAdminAndLogin("funding-admin@example.com");
        var accountId = createAccount(owner, "INR");

        mockMvc.perform(authenticated(get("/api/accounts/{id}", accountId), owner))
                .andExpect(jsonPath("$.balance").value(0.00));
        mockMvc.perform(authenticated(post("/api/admin/accounts/{id}/fundings", accountId), owner)
                        .header("Idempotency-Key", "customer-funding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundingRequest("75.00", "customer-attempt")))
                .andExpect(status().isForbidden());
        mockMvc.perform(authenticated(get("/api/admin/accounts"), owner))
                .andExpect(status().isForbidden());

        var first = fundAccount(admin, accountId, "75.00", "cash-deposit-42", "admin-funding-001");
        var replay = fundAccount(admin, accountId, "75.00", "cash-deposit-42", "admin-funding-001");
        org.assertj.core.api.Assertions.assertThat(replay).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThat(accountFundingRepository.count()).isOne();
        mockMvc.perform(authenticated(get("/api/accounts/{id}", accountId), owner))
                .andExpect(jsonPath("$.balance").value(75.00));
        mockMvc.perform(authenticated(get("/api/admin/users?size=100"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(authenticated(get("/api/admin/accounts?size=100"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(authenticated(post("/api/admin/accounts/{id}/fundings", accountId), admin)
                        .header("Idempotency-Key", "admin-funding-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundingRequest("80.00", "cash-deposit-42")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private AuthenticatedUser registerAndLogin(String email) throws Exception {
        var response = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(userRequest(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = UUID.fromString(JsonPath.read(response, "$.id"));
        var tokens = login(email);
        return new AuthenticatedUser(id, tokens.accessToken(), tokens.refreshToken());
    }

    private AuthenticatedUser login(String email) throws Exception {
        var response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new AuthenticatedUser(null, JsonPath.read(response, "$.accessToken"),
                JsonPath.read(response, "$.refreshToken"));
    }

    private AuthenticatedUser registerAdminAndLogin(String email) throws Exception {
        adminProvisioningService.provision(email, PASSWORD);
        adminProvisioningService.provision(email.toUpperCase(), PASSWORD);
        var id = userRepository.findByEmail(email).orElseThrow().getId();
        var tokens = login(email);
        return new AuthenticatedUser(id, tokens.accessToken(), tokens.refreshToken());
    }

    private UUID createAccount(AuthenticatedUser user, String currency) throws Exception {
        var response = mockMvc.perform(authenticated(post("/api/accounts"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"currency\":\"%s\"}"
                                .formatted(user.id(), currency)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private UUID fundAccount(AuthenticatedUser admin, UUID accountId, String amount, String reference,
            String idempotencyKey) throws Exception {
        var response = mockMvc.perform(authenticated(post("/api/admin/accounts/{id}/fundings", accountId), admin)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundingRequest(amount, reference)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private String fundingRequest(String amount, String reference) {
        return "{\"amount\":%s,\"currency\":\"INR\",\"reference\":\"%s\"}"
                .formatted(amount, reference);
    }

    private <T extends org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> T authenticated(
            T request, AuthenticatedUser user) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
        return request;
    }

    private String userRequest(String email) {
        return "{\"email\":\"%s\",\"firstName\":\"Trade\",\"lastName\":\"Flow\",\"password\":\"%s\"}"
                .formatted(email, PASSWORD);
    }

    private String transferRequest(UUID source, UUID destination, String amount) {
        return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%s,\"currency\":\"INR\"}"
                .formatted(source, destination, amount);
    }

    private record AuthenticatedUser(UUID id, String accessToken, String refreshToken) {
    }
}
