package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response body returned on successful URL shortening ({@code POST /api/shorten}).
 */
@Getter
@AllArgsConstructor
public class ShortenResponse {
    /** The short code segment (e.g. {@code abc123}). */
    private String shortCode;
    /** The full redirect URL callers should share (e.g. {@code http://localhost:8080/abc123}). */
    private String shortUrl;
    /** The original long URL this code resolves to. */
    private String longUrl;
}
