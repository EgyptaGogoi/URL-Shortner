package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Uniform error response body returned for all API error conditions.
 * Stack traces and internal details are never exposed to the client.
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {
    /** Human-readable description of the error, safe to display to end users. */
    private String error;
}
