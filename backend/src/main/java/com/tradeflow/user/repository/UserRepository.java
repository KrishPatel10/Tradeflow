package com.tradeflow.user.repository;

import java.util.UUID;
import java.util.Optional;

import com.tradeflow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
