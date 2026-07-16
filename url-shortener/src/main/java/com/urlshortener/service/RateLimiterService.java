package com.urlshortener.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP token-bucket rate limiter backed by Bucket4j.
 * Each IP gets a fresh bucket on first request; buckets live for the lifetime of the process.
 * The bandwidth limit (10 req/min) is defined in {@link com.urlshortener.config.RateLimiterConfig}.
 */
@Service
public class RateLimiterService {

    private final Bandwidth bandwidth;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(Bandwidth bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * Attempts to consume one token from the bucket assigned to {@code ip}.
     *
     * @param ip client IP address used as the rate-limit key
     * @return {@code true} if a token was available and consumed; {@code false} if the limit is exhausted
     */
    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, k -> Bucket.builder().addLimit(bandwidth).build())
            .tryConsume(1);
    }
}
