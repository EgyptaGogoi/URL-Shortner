package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.AliasAlreadyTakenException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlShortenerController.class)
@TestPropertySource(properties = "app.base-url=http://localhost:8080")
class UrlShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlShortenerService urlShortenerService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/shorten
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/shorten")
    class Shorten {

        @Test
        @DisplayName("F1 — valid request returns 201 with shortCode, shortUrl, and longUrl")
        void shorten_validRequest_returns201WithBody() throws Exception {
            ShortenResponse response = new ShortenResponse(
                "abc123", "http://localhost:8080/abc123", "https://www.example.com");
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any())).thenReturn(response);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("https://www.example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.longUrl").value("https://www.example.com"));
        }

        @Test
        @DisplayName("F4 — custom alias in request is forwarded to the service")
        void shorten_withCustomAlias_returns201() throws Exception {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("mylink");
            ShortenResponse response = new ShortenResponse(
                "mylink", "http://localhost:8080/mylink", "https://www.example.com");
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any())).thenReturn(response);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("mylink"));
        }

        @Test
        @DisplayName("F7 — blank URL fails Bean Validation and returns 400 before reaching the service")
        void shorten_blankUrl_returns400WithoutCallingService() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

            verify(urlShortenerService, never()).shorten(any());
        }

        @Test
        @DisplayName("F7 — missing URL field returns 400")
        void shorten_missingUrl_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("F7 — invalid alias (special characters) fails Bean Validation and returns 400")
        void shorten_invalidAlias_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("bad alias!");

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("F7 — service rejects URL (invalid scheme or SSRF) returns 400 with error message")
        void shorten_serviceRejectsUrl_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any()))
                .thenThrow(new InvalidUrlException("Only http and https schemes are allowed"));

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("https://www.example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only http and https schemes are allowed"));
        }

        @Test
        @DisplayName("F4 — taken alias returns 409 with error message")
        void shorten_aliasTaken_returns409() throws Exception {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("taken");
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any()))
                .thenThrow(new AliasAlreadyTakenException("taken"));

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("F5 — exceeding rate limit returns 429 without calling the service")
        void shorten_rateLimitExceeded_returns429() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(false);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("https://www.example.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists());

            verify(urlShortenerService, never()).shorten(any());
        }

        @Test
        @DisplayName("N4 — endpoint is publicly accessible without authentication")
        void shorten_noAuthRequired_requestIsAccepted() throws Exception {
            ShortenResponse response = new ShortenResponse(
                "abc123", "http://localhost:8080/abc123", "https://www.example.com");
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any())).thenReturn(response);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("https://www.example.com"))))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("F4 — alias of exactly 10 characters is accepted (boundary maximum)")
        void shorten_aliasTenChars_returns201() throws Exception {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("abcdefghij"); // exactly 10 chars
            ShortenResponse response = new ShortenResponse(
                "abcdefghij", "http://localhost:8080/abcdefghij", "https://www.example.com");
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any())).thenReturn(response);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abcdefghij"));
        }

        @Test
        @DisplayName("F4 — alias exceeding 10 characters fails @Size validation and returns 400")
        void shorten_aliasTooLong_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("elevenChars"); // 11 chars — exceeds @Size(max=10)

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

            verify(urlShortenerService, never()).shorten(any());
        }

        @Test
        @DisplayName("F4 — empty string alias fails @Size(min=1) validation and returns 400")
        void shorten_emptyAlias_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://www.example.com\",\"customAlias\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

            verify(urlShortenerService, never()).shorten(any());
        }

        @Test
        @DisplayName("F7 — URL at exactly 2048 characters passes @Size validation (boundary maximum)")
        void shorten_urlAtMaxLength_returns201() throws Exception {
            // "https://www.example.com/" is 24 chars; 24 + 2024 = 2048
            String maxUrl = "https://www.example.com/" + "a".repeat(2024);
            ShortenResponse response = new ShortenResponse(
                "abc123", "http://localhost:8080/abc123", maxUrl);
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            when(urlShortenerService.shorten(any())).thenReturn(response);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"" + maxUrl + "\"}"))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("F7 — URL exceeding 2048 characters fails @Size validation and returns 400")
        void shorten_urlTooLong_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
            // "https://www.example.com/" is 24 chars; 24 + 2025 = 2049
            String tooLong = "https://www.example.com/" + "a".repeat(2025);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

            verify(urlShortenerService, never()).shorten(any());
        }

        @Test
        @DisplayName("malformed JSON body returns 400")
        void shorten_malformedJson_returns400() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(true);

            mockMvc.perform(post("/api/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not-valid-json}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("request without Content-Type application/json returns 415 Unsupported Media Type")
        void shorten_noContentType_returns415() throws Exception {
            mockMvc.perform(post("/api/shorten")
                    .content("{\"url\":\"https://www.example.com\"}"))
                .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /{code}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{code}")
    class Redirect {

        @Test
        @DisplayName("F2 — known code returns 301 with Location header set to the original URL")
        void redirect_knownCode_returns301WithLocationHeader() throws Exception {
            when(urlShortenerService.resolve("abc123")).thenReturn("https://www.example.com");

            mockMvc.perform(get("/abc123"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://www.example.com"));
        }

        @Test
        @DisplayName("F6 — unknown code returns 404 with error message")
        void redirect_unknownCode_returns404() throws Exception {
            when(urlShortenerService.resolve("nope"))
                .thenThrow(new UrlNotFoundException("nope"));

            mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("F5 — GET endpoint is not rate-limited (rate limiter is never consulted)")
        void redirect_isNotRateLimited() throws Exception {
            when(urlShortenerService.resolve("abc123")).thenReturn("https://www.example.com");

            mockMvc.perform(get("/abc123"))
                .andExpect(status().isMovedPermanently());

            verify(rateLimiterService, never()).tryConsume(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helper
    // ─────────────────────────────────────────────────────────────────────────

    private ShortenRequest requestFor(String url) {
        ShortenRequest r = new ShortenRequest();
        r.setUrl(url);
        return r;
    }
}
