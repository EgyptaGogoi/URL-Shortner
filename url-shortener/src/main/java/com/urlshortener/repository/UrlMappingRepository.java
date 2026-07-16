package com.urlshortener.repository;

import com.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UrlMapping}.
 * All derived queries translate directly to indexed column lookups.
 */
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Finds a mapping by its short code; used during redirect resolution.
     *
     * @param shortCode the 6-character (or custom-alias) code
     * @return the mapping, or empty if the code does not exist
     */
    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Finds the canonical system-generated mapping for a URL hash; used for deduplication.
     * Custom aliases are excluded so that the same long URL can have multiple aliases
     * without interfering with the canonical entry.
     *
     * @param urlHash SHA-256 hex digest of the long URL
     * @return the existing canonical mapping, or empty if none exists yet
     */
    Optional<UrlMapping> findByUrlHashAndCustomAliasIsFalse(String urlHash);

    /**
     * Checks whether a short code is already registered; used to validate custom alias availability.
     *
     * @param shortCode the alias to check
     * @return {@code true} if the code is taken
     */
    boolean existsByShortCode(String shortCode);
}
