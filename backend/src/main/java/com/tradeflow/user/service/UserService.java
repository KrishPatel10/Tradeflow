package com.tradeflow.user.service;

import java.util.Locale;
import java.time.Duration;
import java.util.UUID;

import com.tradeflow.common.api.PageResponse;
import com.tradeflow.common.exception.ConflictException;
import com.tradeflow.common.exception.ResourceNotFoundException;
import com.tradeflow.user.dto.CreateUserRequest;
import com.tradeflow.user.dto.UserResponse;
import com.tradeflow.user.entity.User;
import com.tradeflow.user.mapper.UserMapper;
import com.tradeflow.user.repository.UserRepository;
import com.tradeflow.security.CurrentUser;
import com.tradeflow.audit.service.AuditService;
import com.tradeflow.cache.CacheAsideService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final CacheAsideService cache;
    private final Duration userCacheTtl;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CurrentUser currentUser,
            AuditService auditService, CacheAsideService cache,
            @Value("${tradeflow.cache.user-ttl:PT5M}") Duration userCacheTtl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.cache = cache;
        this.userCacheTtl = userCacheTtl;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "A user with this email already exists.");
        }

        var user = User.create(email, request.firstName().trim(), request.lastName().trim(),
                passwordEncoder.encode(request.password()));
        var saved = userRepository.save(user);
        auditService.record("USER", saved.getId().toString(), "USER_CREATED", null,
                "{\"status\":\"ACTIVE\"}", saved.getId());
        return UserMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        currentUser.requireSelfOrAdmin(id);
        var cached = cache.get(userCacheKey(id), UserResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        var response = UserMapper.toResponse(findEntity(id));
        cache.put(userCacheKey(id), response, userCacheTtl);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAll(int page, int size) {
        currentUser.requireAdmin();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(userRepository.findAll(pageable), UserMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public User findEntity(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));
    }

    private static String userCacheKey(UUID id) {
        return "tradeflow:user:" + id;
    }
}
