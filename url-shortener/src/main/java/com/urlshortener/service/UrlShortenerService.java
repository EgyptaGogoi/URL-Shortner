package com.urlshortener.service;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.AliasAlreadyTakenException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core business logic for the URL shortener: validates URLs, deduplicates by SHA-256 hash,
 * generates Base62 short codes, and manages the Redis read-through cache.
 */
@Service
public class UrlShortenerService {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 6;
    private static final String CACHE_PREFIX = "url:";
    private static final long CACHE_TTL_SECONDS = 604_800L;

    private final UrlMappingRepository repository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.base-url}")
    private String baseUrl;

    public UrlShortenerService(UrlMappingRepository repository, RedisTemplate<String, String> redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Validates the URL, then creates or returns a short mapping.
     * Routes to {@link #handleCustomAlias} or {@link #handleSystemGenerated} based on the request.
     *
     * @param request validated request containing the long URL and optional custom alias
     * @return the short code, full short URL, and original URL
     * @throws InvalidUrlException if the URL is invalid or targets a private address
     * @throws AliasAlreadyTakenException if a custom alias is requested but already registered
     */
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        validateUrl(request.getUrl());
        String hash = sha256(request.getUrl());
        String alias = request.getCustomAlias();

        if (alias != null && !alias.isBlank()) {
            return handleCustomAlias(request.getUrl(), hash, alias);
        }
        return handleSystemGenerated(request.getUrl(), hash);
    }

    /**
     * Cache-aside lookup: checks Redis first, falls back to PostgreSQL on a miss,
     * and repopulates the cache before returning.
     *
     * @param shortCode the short code to resolve
     * @return the original long URL
     * @throws UrlNotFoundException if no mapping exists for the code
     */
    public String resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            return cached;
        }
        UrlMapping mapping = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));
        cache(shortCode, mapping.getLongUrl());
        return mapping.getLongUrl();
    }

    /** Stores the user-supplied alias if unclaimed; throws 409 otherwise. */
    private ShortenResponse handleCustomAlias(String longUrl, String hash, String alias) {
        if (repository.existsByShortCode(alias)) {
            throw new AliasAlreadyTakenException(alias);
        }
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(longUrl);
        mapping.setUrlHash(hash);
        mapping.setShortCode(alias);
        mapping.setCustomAlias(true);
        repository.save(mapping);
        cache(alias, longUrl);
        return buildResponse(alias, longUrl);
    }

    /**
     * Deduplicates by URL hash. If a canonical entry exists, returns it immediately.
     * Otherwise inserts a placeholder row (UUID-based) to obtain the DB-generated ID,
     * derives the final 6-character code via {@link #generateCode}, then updates the row.
     * The UUID placeholder ensures uniqueness even under concurrent inserts.
     */
    private ShortenResponse handleSystemGenerated(String longUrl, String hash) {
        Optional<UrlMapping> existing = repository.findByUrlHashAndCustomAliasIsFalse(hash);
        if (existing.isPresent()) {
            String code = existing.get().getShortCode();
            cache(code, longUrl);
            return buildResponse(code, longUrl);
        }

        // Insert with a temp unique placeholder to obtain the DB-generated ID
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(longUrl);
        mapping.setUrlHash(hash);
        mapping.setShortCode(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        mapping.setCustomAlias(false);
        mapping = repository.saveAndFlush(mapping);

        String code = generateCode(mapping.getId());
        mapping.setShortCode(code);
        repository.save(mapping);

        cache(code, longUrl);
        return buildResponse(code, longUrl);
    }

    /**
     * Validates URL syntax (RFC 3986), enforces the http/https scheme allowlist,
     * and blocks private/loopback addresses to prevent SSRF (CWE-918).
     * Unresolvable hostnames are permitted — they will fail at redirect time.
     */
    private void validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
            uri.toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new InvalidUrlException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !List.of("http", "https").contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("Only http and https schemes are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must have a valid host");
        }

        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    throw new InvalidUrlException("URL points to a private or restricted address");
                }
            }
        } catch (UnknownHostException ignored) {
            // unresolvable hosts are permitted — redirect will fail naturally
        }
    }

    /**
     * Counter + random Base62 hybrid (decision D4): encodes the DB ID in Base62 to guarantee
     * uniqueness without a collision check, then appends random Base62 chars to reach 6 characters
     * and prevent sequential enumeration.
     */
    private String generateCode(long id) {
        String idPart = toBase62(id);
        StringBuilder sb = new StringBuilder(idPart);
        while (sb.length() < CODE_LENGTH) {
            sb.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    /** Standard Base62 encoding of a non-negative long integer. */
    private String toBase62(long n) {
        if (n == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            sb.append(BASE62.charAt((int) (n % 62)));
            n /= 62;
        }
        return sb.reverse().toString();
    }

    /** SHA-256 hex digest of the long URL — used as a fast dedup key without full TEXT comparison. */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void cache(String shortCode, String longUrl) {
        redisTemplate.opsForValue().set(
            CACHE_PREFIX + shortCode,
            longUrl,
            Duration.ofSeconds(CACHE_TTL_SECONDS)
        );
    }

    private ShortenResponse buildResponse(String code, String longUrl) {
        return new ShortenResponse(code, baseUrl + "/" + code, longUrl);
    }
}
