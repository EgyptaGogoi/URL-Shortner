# URL Shortener — Project Log

**Stack:** Java / Spring Boot · PostgreSQL · Redis  
**Started:** 2026-07-15  
**Partner:** Claude (Sonnet 4.6)

---

## Task 1 — Requirement Gathering

**Status:** Complete  
**Date:** 2026-07-15

### Decisions Made

| Question | Decision |
|---|---|
| Use case | Public-facing with abuse prevention |
| Tech stack | Java / Spring Boot |
| Database | Redis (cache) + PostgreSQL (persistence) |
| Authentication | None — rate limiting by IP instead |
| Duplicate URLs | Deduplicate by default; custom alias creates a new mapping |
| Features in scope | Custom aliases, deduplication |

### Functional Requirements

| ID | Requirement | Detail |
|---|---|---|
| F1 | Shorten a URL | Accept a long URL, return a short code (e.g. `/abc123`) |
| F2 | Redirect | `GET /{code}` → `301` redirect to original URL |
| F3 | Deduplication | Same long URL → same short code; no duplicate entries |
| F4 | Custom alias | User may supply a preferred short code; creates a separate mapping even if the URL already exists |
| F5 | Abuse prevention | IP-based rate limiting on write endpoints |
| F6 | Unknown code handling | `GET /{code}` with a non-existent code returns `404 Not Found` |
| F7 | URL validation | Incoming URLs must be syntactically valid per RFC 3986, use `http` or `https` scheme only, and must not point to private/loopback addresses (`localhost`, `127.0.0.1`, `10.x`, `192.168.x`, `172.16–31.x`) |

### Non-Functional Requirements

| ID | Requirement | Detail |
|---|---|---|
| N1 | Low-latency redirect | Redis as read-through cache for redirect lookups |
| N2 | Persistent storage | PostgreSQL via Spring Data JPA as source of truth |
| N3 | Idempotent creation | Repeated `POST` with same URL returns the existing code |
| N4 | No authentication | Write endpoints are public; protected by rate limiting only |

### Out of Scope (v1)

- User accounts / link management dashboard
- Click analytics
- Link expiry / TTL
- Admin panel
- Max custom aliases per URL (no user accounts = no per-user enforcement; rate limiting handles abuse — revisit if user accounts are added)

### API Surface (high level)

```
POST  /api/shorten    → create short link (with optional custom alias)
GET   /{code}         → redirect to original URL
```

---

## Decision Log

| ID | Decision | Rationale |
|---|---|---|
| D1 | Use `301` redirect (not `302`) | No update/delete operations — mappings are permanent. 301 lets browsers cache the redirect, reducing server load and latency on repeat visits. |
| D2 | Short code length: 6 characters (Base62) | 62⁶ ≈ 56 billion unique codes — sufficient headroom for any realistic scale. Base62 charset: `a–z`, `A–Z`, `0–9`. Full BOTE skipped; this is the only estimation that influences a design decision. |
| D3 | Accept `http` and `https` schemes only | Other schemes (`ftp`, `mailto`, `file`, `gopher`, etc.) don't work reliably as browser redirect targets. Private/loopback IPs blocked to prevent SSRF. |
| D4 | Short code generation: Counter + Random hybrid (Base62) | DB auto-increment ID encoded in Base62 + random Base62 chars padded to 6 chars total. Counter guarantees uniqueness (no collision check needed), random suffix prevents enumeration. Flow: INSERT → get ID → generate code → UPDATE record. |

<!-- New tasks will be appended below this line -->

---

## Task 2 — System Design

**Status:** Complete  
**Date:** 2026-07-17

### DB Schema — `url_mappings` (PostgreSQL)

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY |
| `short_code` | `VARCHAR(10)` | UNIQUE, NOT NULL |
| `long_url` | `TEXT` | NOT NULL |
| `url_hash` | `VARCHAR(64)` | NOT NULL, INDEX |
| `is_custom_alias` | `BOOLEAN` | NOT NULL, DEFAULT false |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT now() |

- `url_hash` = SHA-256 of `long_url` — fast deduplication without full TEXT comparison
- `is_custom_alias = false` → system-generated (canonical entry for that URL)
- `is_custom_alias = true` → user-provided alias (multiple allowed per URL)

### Layer Structure

```
com.urlshortener
├── controller    → receives HTTP request, delegates to service, returns response
├── service       → all business logic: validate, deduplicate, generate code, cache
├── repository    → Spring Data JPA interface; talks to PostgreSQL
├── model         → UrlMapping JPA entity (maps to url_mappings table)
├── dto           → ShortenRequest, ShortenResponse (API contract, separate from DB model)
├── config        → RedisConfig, RateLimiterConfig
└── exception     → InvalidUrlException (400), AliasAlreadyTakenException (409),
                    UrlNotFoundException (404), RateLimitExceededException (429)
```

### Redis Caching Strategy (Cache-aside)

- **Key:** `url:{short_code}` → **Value:** `long_url`
- **TTL:** 1 week (604,800 seconds) — on expiry, re-fetched from PostgreSQL and re-cached
- On `GET /{code}`: check Redis first → hit = redirect immediately, miss = query DB → cache → redirect

### Rate Limiting

- **Library:** Bucket4j + Redis
- **Scope:** `POST /api/shorten` only, keyed by client IP
- **Limit:** 10 requests per minute per IP → exceeds = `429 Too Many Requests`

### Decisions Added

| ID | Decision | Rationale |
|---|---|---|
| D5 | Redis TTL: 1 week | Mappings are permanent in DB; TTL evicts stale cache entries. Miss just re-populates from PostgreSQL — no data loss. |
| D6 | Rate limit: 10 req/min/IP via Bucket4j + Redis | Redis already in stack — no extra infra. Bucket4j + Redis survives restarts and works across multiple instances. |
