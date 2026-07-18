package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /api/shorten}.
 */
@Schema(description = "Request body for shortening a URL")
@Getter
@Setter
public class ShortenRequest {

    /**
     * The long URL to shorten. Must use the {@code http} or {@code https} scheme
     * and must not target a private or loopback address (SSRF prevention).
     */
    @Schema(
        description = "The long URL to shorten. Must use http or https and must not target private or loopback addresses.",
        example = "https://www.example.com/some/very/long/path?with=query&params=here",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "URL must not be blank")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String url;

    /**
     * Optional preferred short code. Must be 1–10 alphanumeric characters.
     * Omit (or pass {@code null}) to receive a system-generated 6-character code.
     * A separate mapping row is created even if the same long URL already has a canonical entry.
     */
    @Schema(
        description = "Optional preferred short code (1–10 alphanumeric characters). Omit to receive a system-generated 6-character code.",
        example = "mylink",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Size(min = 1, max = 10, message = "Custom alias must be between 1 and 10 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Custom alias must be alphanumeric")
    private String customAlias;
}
