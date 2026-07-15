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
| F2 | Redirect | `GET /{code}` → `302` redirect to original URL |
| F3 | Deduplication | Same long URL → same short code; no duplicate entries |
| F4 | Custom alias | User may supply a preferred short code; creates a separate mapping even if the URL already exists |
| F5 | Abuse prevention | IP-based rate limiting on write endpoints |

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

<!-- New tasks will be appended below this line -->
