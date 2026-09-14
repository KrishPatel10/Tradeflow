package com.tradeflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AdminProvisioningService provisioningService;
    private final String email;
    private final String password;

    public AdminBootstrap(AdminProvisioningService provisioningService,
            @Value("${tradeflow.bootstrap.admin.email:}") String email,
            @Value("${tradeflow.bootstrap.admin.password:}") String password) {
        this.provisioningService = provisioningService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Both TradeFlow bootstrap admin email and password must be configured.");
        }
        if (password.length() < 12) {
            throw new IllegalStateException("TradeFlow bootstrap admin password must contain at least 12 characters.");
        }
        provisioningService.provision(email, password);
    }
}
