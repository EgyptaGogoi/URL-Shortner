package com.urlshortener.controller;

import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller exposing the two public API endpoints:
 * URL shortening ({@code POST /api/shorten}) and redirect ({@code GET /{code}}).
 */
@Tag(name = "URL Shortener", description = "Create short links and resolve them to their original URLs")
@RestController
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;
    private final RateLimiterService rateLimiterService;

    public UrlShortenerController(UrlShortenerService urlShortenerService, RateLimiterService rateLimiterService) {
        this.urlShortenerService = urlShortenerService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Creates a short link for the submitted URL.
     * Enforces per-IP rate limiting; returns 429 if the limit is exceeded, 201 on success.
     *
     * @param request     validated request body containing the long URL and optional custom alias
     * @param httpRequest used to extract the client's remote address for rate limiting
     * @return the generated short code, full short URL, and original URL
     */
    @Operation(
        summary = "Shorten a URL",
        description = "Accepts a long URL and returns a short code. " +
                      "Supply `customAlias` to request a specific code; omit it for a system-generated one. " +
                      "Identical long URLs return the same code (deduplication). " +
                      "Rate-limited to **10 requests per minute** per IP."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Short link created successfully",
            content = @Content(schema = @Schema(implementation = ShortenResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid URL or request validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Requested custom alias is already taken",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded — retry after one minute",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest) {
        if (!rateLimiterService.tryConsume(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(urlShortenerService.shorten(request));
    }

    /**
     * Permanently redirects the caller to the original URL mapped to {@code code}.
     * Returns 301 on success, 404 if the code does not exist.
     *
     * @param code the short code segment from the request path
     * @return 301 response with {@code Location} header set to the original URL
     */
    @Operation(
        summary = "Redirect to original URL",
        description = "Looks up the short code in Redis (cache-aside) then PostgreSQL, " +
                      "and issues a **301 permanent redirect** to the original URL. " +
                      "Test this endpoint by pasting the short URL directly into your browser " +
                      "or with curl: `curl -v http://localhost:8080/{code}`"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "301", description = "Permanent redirect — `Location` header contains the original URL"),
        @ApiResponse(responseCode = "404", description = "Short code not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "The short code to resolve", example = "abc123", required = true)
            @PathVariable String code) {
        String longUrl = urlShortenerService.resolve(code);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .location(URI.create(longUrl))
            .build();
    }
}
