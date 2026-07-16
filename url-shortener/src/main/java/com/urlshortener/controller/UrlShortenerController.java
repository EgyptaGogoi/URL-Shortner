package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlShortenerService;
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
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = urlShortenerService.resolve(code);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .location(URI.create(longUrl))
            .build();
    }
}
