package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response body returned on successful URL shortening ({@code POST /api/shorten}).
 */
@Schema(description = "Response body returned on successful URL shortening")
@Getter
@AllArgsConstructor
public class ShortenResponse {

    /** The short code segment (e.g. {@code abc123}). */
    @Schema(description = "The generated or user-supplied short code", example = "abc123")
    private String shortCode;

    /** The full redirect URL callers should share (e.g. {@code http://localhost:8080/abc123}). */
    @Schema(description = "The full short URL to share with others", example = "http://localhost:8080/abc123")
    private String shortUrl;

    /** The original long URL this code resolves to. */
    @Schema(description = "The original long URL this code resolves to", example = "https://www.example.com/some/very/long/path")
    private String longUrl;
}
