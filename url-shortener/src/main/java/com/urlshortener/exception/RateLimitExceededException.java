package com.urlshortener.exception;

/**
 * Thrown when a client IP exceeds 10 POST requests per minute.
 * Maps to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Rate limit exceeded. Please try again later.");
    }
}
