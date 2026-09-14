package com.tradeflow.transfer.repository;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyReservation {

    private final JdbcTemplate jdbcTemplate;
    private final boolean h2;

    public IdempotencyReservation(JdbcTemplate jdbcTemplate, DataSource dataSource) throws SQLException {
        this.jdbcTemplate = jdbcTemplate;
        try (var connection = dataSource.getConnection()) {
            this.h2 = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        }
    }

    public int reserve(String key, String fingerprint) {
        if (h2) {
            return jdbcTemplate.update("""
                    INSERT INTO idempotency_records
                        (idempotency_key, request_fingerprint, status, created_at)
                    SELECT ?, ?, 'PROCESSING', CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (
                        SELECT 1 FROM idempotency_records WHERE idempotency_key = ?
                    )
                    """, key, fingerprint, key);
        }
        return jdbcTemplate.update("""
                INSERT INTO idempotency_records
                    (idempotency_key, request_fingerprint, status, created_at)
                VALUES (?, ?, 'PROCESSING', CURRENT_TIMESTAMP)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, key, fingerprint);
    }
}
