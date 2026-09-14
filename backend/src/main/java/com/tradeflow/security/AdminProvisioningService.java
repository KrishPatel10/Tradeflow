package com.tradeflow.security;

import java.util.Locale;

import com.tradeflow.audit.service.AuditService;
import com.tradeflow.user.entity.User;
import com.tradeflow.user.entity.UserRole;
import com.tradeflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProvisioningService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminProvisioningService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void provision(String rawEmail, String rawPassword) {
        var email = rawEmail.trim().toLowerCase(Locale.ROOT);
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("Bootstrap admin email already belongs to a customer account.");
            }
            return;
        }

        var admin = userRepository.save(User.createAdmin(email, "TradeFlow", "Administrator",
                passwordEncoder.encode(rawPassword)));
        auditService.record("USER", admin.getId().toString(), "ADMIN_BOOTSTRAPPED", null,
                "{\"status\":\"ACTIVE\",\"role\":\"ADMIN\"}", admin.getId());
    }
}
