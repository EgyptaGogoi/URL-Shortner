package com.urlshortener.service;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.AliasAlreadyTakenException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F1 — Shorten a URL
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("F1 — Shorten a URL")
    class ShortenNewUrl {

        @Test
        @DisplayName("returns a 6-character short code and correct response fields for a new URL")
        void shorten_newUrl_createsShortCode() {
            UrlMapping flushed = mappingWithId(1L, "https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenReturn(flushed);
            when(repository.save(any())).thenReturn(flushed);

            ShortenResponse response = service.shorten(requestFor("https://www.example.com"));

            assertNotNull(response.getShortCode());
            assertEquals(6, response.getShortCode().length());
            assertEquals("https://www.example.com", response.getLongUrl());
            assertTrue(response.getShortUrl().startsWith("http://localhost:8080/"));
            verify(repository).saveAndFlush(any());
            verify(repository).save(any());
        }

        @Test
        @DisplayName("short URL is base-url + '/' + short code")
        void shorten_shortUrlIsBaseUrlPlusCode() {
            UrlMapping flushed = mappingWithId(5L, "https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenReturn(flushed);
            when(repository.save(any())).thenReturn(flushed);

            ShortenResponse response = service.shorten(requestFor("https://www.example.com"));

            assertEquals("http://localhost:8080/" + response.getShortCode(), response.getShortUrl());
        }

        @Test
        @DisplayName("generated short code contains only Base62 alphanumeric characters")
        void shorten_generatedCode_isAlphanumericOnly() {
            UrlMapping flushed = mappingWithId(42L, "https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenReturn(flushed);
            when(repository.save(any())).thenReturn(flushed);

            ShortenResponse response = service.shorten(requestFor("https://www.example.com"));

            assertTrue(response.getShortCode().matches("^[a-zA-Z0-9]+$"),
                "Short code must consist solely of Base62 characters");
        }

        @Test
        @DisplayName("N1 — successful shorten populates Redis with key 'url:{code}' and a 1-week TTL")
        void shorten_newUrl_populatesRedisCache() {
            UrlMapping flushed = mappingWithId(1L, "https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenReturn(flushed);
            when(repository.save(any())).thenReturn(flushed);

            ShortenResponse response = service.shorten(requestFor("https://www.example.com"));

            verify(valueOps).set(
                eq("url:" + response.getShortCode()),
                eq("https://www.example.com"),
                eq(Duration.ofSeconds(604_800))
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F3 / N3 — Deduplication / Idempotent creation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("F3/N3 — Deduplication and idempotent creation")
    class Deduplication {

        @Test
        @DisplayName("same long URL returns the existing short code without inserting a new row")
        void shorten_duplicateUrl_returnsExistingCode() {
            UrlMapping existing = new UrlMapping();
            existing.setShortCode("abc123");
            existing.setLongUrl("https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.of(existing));

            ShortenResponse response = service.shorten(requestFor("https://www.example.com"));

            assertEquals("abc123", response.getShortCode());
            verify(repository, never()).saveAndFlush(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("repeated calls with the same URL always return the same code (idempotent)")
        void shorten_repeatedCallsSameUrl_alwaysReturnSameCode() {
            UrlMapping existing = new UrlMapping();
            existing.setShortCode("abc123");
            existing.setLongUrl("https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.of(existing));

            ShortenResponse first  = service.shorten(requestFor("https://www.example.com"));
            ShortenResponse second = service.shorten(requestFor("https://www.example.com"));

            assertEquals(first.getShortCode(), second.getShortCode());
        }

        @Test
        @DisplayName("URLs differing only by query string are hashed differently and stored as distinct entries")
        void shorten_urlsWithDifferentQueryStrings_areStoredSeparately() {
            // IDs 10 → Base62 "A", 11 → Base62 "B" — first char differs, so codes are always distinct
            UrlMapping first  = mappingWithId(10L, "https://www.example.com?a=1");
            UrlMapping second = mappingWithId(11L, "https://www.example.com?a=2");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenReturn(first).thenReturn(second);
            when(repository.save(any())).thenReturn(new UrlMapping());

            ShortenResponse r1 = service.shorten(requestFor("https://www.example.com?a=1"));
            ShortenResponse r2 = service.shorten(requestFor("https://www.example.com?a=2"));

            verify(repository, times(2)).saveAndFlush(any());
            assertNotEquals(r1.getShortCode(), r2.getShortCode(),
                "Distinct URLs must produce distinct short codes");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F4 — Custom alias
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("F4 — Custom alias")
    class CustomAlias {

        @Test
        @DisplayName("stores custom alias as a separate mapping row with isCustomAlias=true")
        void shorten_customAlias_createsAliasMapping() {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("mylink");
            when(repository.existsByShortCode("mylink")).thenReturn(false);
            when(repository.save(any())).thenReturn(new UrlMapping());

            ShortenResponse response = service.shorten(request);

            assertEquals("mylink", response.getShortCode());
            verify(repository).save(argThat(m -> m.isCustomAlias() && "mylink".equals(m.getShortCode())));
        }

        @Test
        @DisplayName("custom alias creates a new row even when the same long URL already has a canonical entry")
        void shorten_customAliasForExistingUrl_createsNewRow() {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("myalias");
            when(repository.existsByShortCode("myalias")).thenReturn(false);
            when(repository.save(any())).thenReturn(new UrlMapping());

            service.shorten(request);

            verify(repository).save(argThat(UrlMapping::isCustomAlias));
            verify(repository, never()).findByUrlHashAndCustomAliasIsFalse(any());
        }

        @Test
        @DisplayName("requesting an already-taken alias throws AliasAlreadyTakenException (409)")
        void shorten_takenAlias_throwsAliasAlreadyTakenException() {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias("taken");
            when(repository.existsByShortCode("taken")).thenReturn(true);

            assertThrows(AliasAlreadyTakenException.class, () -> service.shorten(request));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("blank alias string (isBlank=true) falls through to system code generation — no alias lookup performed")
        void shorten_blankAlias_treatedAsNoAlias() {
            ShortenRequest request = requestFor("https://www.example.com");
            request.setCustomAlias(""); // blank → service's alias.isBlank() check routes to system generation
            UrlMapping flushed = mappingWithId(1L, "https://www.example.com");
            when(repository.findByUrlHashAndCustomAliasIsFalse(anyString())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenReturn(flushed);
            when(repository.save(any())).thenReturn(flushed);

            ShortenResponse response = service.shorten(request);

            assertNotNull(response.getShortCode());
            assertEquals(6, response.getShortCode().length());
            verify(repository, never()).existsByShortCode(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F7 — URL validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("F7 — URL validation")
    class UrlValidation {

        @ParameterizedTest(name = "rejects invalid/disallowed URL: [{0}]")
        @ValueSource(strings = {
            "not-a-url",
            "://missing-scheme",
            "ftp://files.example.com",
            "mailto:user@example.com",
            "file:///etc/passwd"
        })
        @DisplayName("rejects URLs with invalid syntax or non-http(s) schemes")
        void shorten_invalidOrDisallowedUrl_throwsInvalidUrlException(String url) {
            assertThrows(InvalidUrlException.class, () -> service.shorten(requestFor(url)));
        }

        @Test
        @DisplayName("loopback address 127.0.0.1 is rejected (SSRF — CWE-918)")
        void shorten_loopbackIp_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://127.0.0.1/secret")));
        }

        @Test
        @DisplayName("localhost hostname is rejected (SSRF — CWE-918)")
        void shorten_localhostHostname_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://localhost/internal")));
        }

        @Test
        @DisplayName("private IP range 192.168.x.x is rejected (SSRF — CWE-918)")
        void shorten_privateIpRange192_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://192.168.1.100/admin")));
        }

        @Test
        @DisplayName("private IP range 10.x.x.x is rejected (SSRF — CWE-918)")
        void shorten_privateIpRange10_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://10.0.0.1/internal")));
        }

        @Test
        @DisplayName("link-local address 169.254.x.x is rejected to block cloud metadata endpoints")
        void shorten_linkLocalAddress_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://169.254.169.254/latest/meta-data/")));
        }

        @Test
        @DisplayName("IPv6 loopback address [::1] is rejected (SSRF — CWE-918)")
        void shorten_ipv6LoopbackAddress_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://[::1]/path")));
        }

        @Test
        @DisplayName("private IP range 172.16.x.x is rejected (SSRF — CWE-918)")
        void shorten_privateIpRange172_throwsInvalidUrlException() {
            assertThrows(InvalidUrlException.class,
                () -> service.shorten(requestFor("http://172.16.0.1/private")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F2 / N1 — Redirect with Redis cache-aside
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("F2/N1 — Redirect with Redis cache-aside")
    class Resolve {

        @Test
        @DisplayName("cache hit returns the long URL from Redis without touching the database")
        void resolve_cacheHit_returnsWithoutDbQuery() {
            when(valueOps.get("url:abc123")).thenReturn("https://www.example.com");

            String result = service.resolve("abc123");

            assertEquals("https://www.example.com", result);
            verify(repository, never()).findByShortCode(any());
        }

        @Test
        @DisplayName("cache miss queries the database and repopulates Redis with a 1-week TTL")
        void resolve_cacheMiss_queriesDbAndCachesResult() {
            UrlMapping mapping = new UrlMapping();
            mapping.setShortCode("abc123");
            mapping.setLongUrl("https://www.example.com");
            when(valueOps.get("url:abc123")).thenReturn(null);
            when(repository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

            String result = service.resolve("abc123");

            assertEquals("https://www.example.com", result);
            verify(valueOps).set(eq("url:abc123"), eq("https://www.example.com"), any(Duration.class));
        }

        @Test
        @DisplayName("N1 — cache TTL on a miss repopulation is exactly 7 days (604 800 seconds)")
        void resolve_cacheMiss_ttlIsOneWeek() {
            UrlMapping mapping = new UrlMapping();
            mapping.setShortCode("abc123");
            mapping.setLongUrl("https://www.example.com");
            when(valueOps.get("url:abc123")).thenReturn(null);
            when(repository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

            service.resolve("abc123");

            verify(valueOps).set("url:abc123", "https://www.example.com", Duration.ofSeconds(604_800));
        }

        // ── F6 — Unknown code handling ────────────────────────────────────────

        @Test
        @DisplayName("F6 — non-existent short code throws UrlNotFoundException (404)")
        void resolve_unknownCode_throwsUrlNotFoundException() {
            when(valueOps.get("url:nope")).thenReturn(null);
            when(repository.findByShortCode("nope")).thenReturn(Optional.empty());

            assertThrows(UrlNotFoundException.class, () -> service.resolve("nope"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ShortenRequest requestFor(String url) {
        ShortenRequest r = new ShortenRequest();
        r.setUrl(url);
        return r;
    }

    private UrlMapping mappingWithId(Long id, String longUrl) {
        UrlMapping m = new UrlMapping();
        m.setId(id);
        m.setLongUrl(longUrl);
        m.setShortCode("placeholder");
        return m;
    }
}
