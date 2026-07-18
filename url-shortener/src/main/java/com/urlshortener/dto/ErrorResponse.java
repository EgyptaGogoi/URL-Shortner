package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Uniform error response body returned for all API error conditions.
 * Stack traces and internal details are never exposed to the client.
 */
@Schema(description = "Error response body returned for all API error conditions")
@Getter
@AllArgsConstructor
public class ErrorResponse {

    /** Human-readable description of the error, safe to display to end users. */
    @Schema(description = "Human-readable description of the error", example = "Only http and https schemes are allowed")
    private String error;
}
