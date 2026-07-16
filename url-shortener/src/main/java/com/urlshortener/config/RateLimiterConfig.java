package com.urlshortener.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Defines the token-bucket bandwidth used by {@link com.urlshortener.service.RateLimiterService}:
 * 10 requests per minute per IP, refilled greedily (tokens are added back as time passes,
 * not in a single batch at the start of the next window).
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Classic greedy bandwidth: capacity 10, refill 10 tokens per minute.
     *
     * @return the {@link Bandwidth} bean injected into {@link com.urlshortener.service.RateLimiterService}
     */
    @Bean
    public Bandwidth rateLimitBandwidth() {
        return Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
    }
}
