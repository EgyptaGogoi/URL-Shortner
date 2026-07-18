package com.urlshortener.service;

import io.github.bucket4j.Bandwidth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for F5 — IP-based rate limiting on write endpoints.
 * Uses a bucket capacity of 3 to keep tests fast and readable.
 */
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        Bandwidth bandwidth = Bandwidth.builder()
            .capacity(3)
            .refillGreedy(3, Duration.ofMinutes(1))
            .build();
        rateLimiterService = new RateLimiterService(bandwidth);
    }

    @Nested
    @DisplayName("F5 — Rate limiting per IP")
    class RateLimiting {

        @Test
        @DisplayName("requests within the limit are accepted (returns true)")
        void tryConsume_withinLimit_returnsTrue() {
            assertTrue(rateLimiterService.tryConsume("1.2.3.4"));
            assertTrue(rateLimiterService.tryConsume("1.2.3.4"));
            assertTrue(rateLimiterService.tryConsume("1.2.3.4"));
        }

        @Test
        @DisplayName("request exceeding the limit is rejected (returns false)")
        void tryConsume_limitExceeded_returnsFalse() {
            rateLimiterService.tryConsume("1.2.3.4");
            rateLimiterService.tryConsume("1.2.3.4");
            rateLimiterService.tryConsume("1.2.3.4");

            assertFalse(rateLimiterService.tryConsume("1.2.3.4"));
        }

        @Test
        @DisplayName("each IP has an independent bucket — exhausting one does not affect another")
        void tryConsume_differentIps_haveIndependentBuckets() {
            rateLimiterService.tryConsume("1.1.1.1");
            rateLimiterService.tryConsume("1.1.1.1");
            rateLimiterService.tryConsume("1.1.1.1");

            assertTrue(rateLimiterService.tryConsume("2.2.2.2"),
                "Second IP should still have capacity after first IP is exhausted");
        }

        @Test
        @DisplayName("first request for any IP is always accepted")
        void tryConsume_firstRequestForNewIp_isAccepted() {
            assertTrue(rateLimiterService.tryConsume("brand.new.ip.1"));
            assertTrue(rateLimiterService.tryConsume("brand.new.ip.2"));
        }

        @Test
        @DisplayName("once the bucket is exhausted all subsequent requests continue to be rejected")
        void tryConsume_afterExhaustion_remainsRejected() {
            rateLimiterService.tryConsume("1.2.3.4");
            rateLimiterService.tryConsume("1.2.3.4");
            rateLimiterService.tryConsume("1.2.3.4"); // capacity reached

            assertFalse(rateLimiterService.tryConsume("1.2.3.4"), "first over-limit");
            assertFalse(rateLimiterService.tryConsume("1.2.3.4"), "second over-limit");
            assertFalse(rateLimiterService.tryConsume("1.2.3.4"), "third over-limit");
        }
    }
}
