package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single short-to-long URL mapping in the {@code url_mappings} table.
 * Both system-generated codes and custom aliases are stored as rows in this table;
 * {@link #isCustomAlias()} distinguishes them.
 */
@Entity
@Table(name = "url_mappings")
@Getter
@Setter
@NoArgsConstructor
public class UrlMapping {

    /** Auto-incremented surrogate key; used as the counter input for Base62 code generation. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The 6-character system code or user-supplied alias (up to 10 characters). */
    @Column(name = "short_code", nullable = false, unique = true, length = 10)
    private String shortCode;

    /** The original destination URL. */
    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    /** SHA-256 hex digest of {@link #longUrl}; indexed for fast deduplication without full TEXT comparison. */
    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    /**
     * {@code false} for the canonical system-generated entry; {@code true} for every user-supplied alias.
     * A single long URL may have exactly one canonical entry and multiple custom aliases.
     */
    @Column(name = "is_custom_alias", nullable = false)
    private boolean customAlias = false;

    /** Timestamp set once at insert time via {@link #onCreate()}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
