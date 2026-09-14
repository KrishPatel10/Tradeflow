package com.tradeflow.cache;

import java.time.Duration;
import java.util.Optional;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CacheAsideService {

    private static final Logger log = LoggerFactory.getLogger(CacheAsideService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    public CacheAsideService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry,
            @Value("${tradeflow.cache.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            var value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                meterRegistry.counter("tradeflow.cache.requests", "result", "miss").increment();
                return Optional.empty();
            }
            meterRegistry.counter("tradeflow.cache.requests", "result", "hit").increment();
            return Optional.of(objectMapper.readValue(value, type));
        } catch (RuntimeException exception) {
            recordFailure("read", key, exception);
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException exception) {
            recordFailure("write", key, exception);
        }
    }

    public void evictAfterCommit(String... keys) {
        if (!enabled) {
            return;
        }
        Runnable eviction = () -> {
            try {
                redisTemplate.delete(java.util.List.of(keys));
            } catch (RuntimeException exception) {
                recordFailure("evict", String.join(",", keys), exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }

    private void recordFailure(String operation, String key, Exception exception) {
        meterRegistry.counter("tradeflow.cache.errors", "operation", operation).increment();
        log.warn("Redis cache {} failed for key {}; continuing with PostgreSQL", operation, key,
                exception);
    }
}
