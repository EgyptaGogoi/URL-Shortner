# One Page : Write-up

## 1. What did you ask the AI to do, and what did you write or decide yourself?

I used AI as a pair programmer throughout, but the design decisions were mine. I first defined the requirements, including the functional scope (shortening, redirects, deduplication, custom aliases) and non-functional requirements (rate limiting, SSRF protection, persistence, latency), while explicitly excluding features like user accounts and analytics. I chose the technology stack: Spring Boot, PostgreSQL for durable storage, and Redis for low-latency redirects.

Before implementation, I wrote the requirements specification (F1–F7, N1–N4) and a decision log documenting the rationale behind each architectural choice. Two key design decisions were the **6-character Base62 short-code scheme**, generated from the database ID with random padding to prevent enumeration, and the **validation contract**, which split responsibility between Bean Validation for request validation and service-layer checks for URL schemes and SSRF protection.

I also defined the core business rules: identical URLs always deduplicate to the same short code, custom aliases always create separate mappings, `is_custom_alias` distinguishes canonical and custom entries, and SHA-256 URL hashes are stored and indexed for efficient deduplication.

For testing, I decided the coverage strategy, ensuring every functional requirement was tested and that SSRF cases (loopback, private, link-local, and IPv6 addresses) each had dedicated tests. The AI then implemented the code—including repositories, services, Flyway migrations, Docker configuration, Swagger annotations, and test suites—while following the architecture and rules I had defined.


---

## 2. Where did you override, correct, or throw away the AI’s output — and why?

The first correction was the **redirect status code**. The AI initially suggested a `302 Found` (temporary redirect). I changed it to `301 Moved Permanently` because the service has no update or delete functionality — once a short code is created, it is permanent. A 301 lets browsers and CDNs cache the redirect, which reduces load on the service. A 302 would have been a silent performance pessimisation with no upside given the data model.

The second correction was the **`customAlias` field**. The AI's initial implementation treated it as a required field — any request without it would fail validation. I changed it to optional (`requiredMode = NOT_REQUIRED`, no `@NotNull`) because the whole point of the field is convenience: users who don't care about a memorable slug should get a system-generated code without having to explicitly pass anything. Making it required would have broken the primary happy path.

I also **rejected user management** entirely when the AI floated it as a possible feature. It would have added a full auth layer, session handling, and ownership checks — scope that wasn't needed to satisfy the core requirements and would have pushed the project well past the time budget.

I fixed the **project folder structure** the AI scaffolded, and I identified that the Swagger "Try it out" was broken on the GET endpoint due to a browser CORS restriction when following 301s — the AI had not flagged this. Once I surfaced the root cause, I directed the fix (disabling Execute on the GET endpoint via `supported-submit-methods`).

---

## 3. The two or three biggest trade-offs you made, and the alternatives you considered.

**301 vs 302.** Covered above. The downside of 301 is that browsers cache it indefinitely, so if the destination ever needed to change, existing users' browsers would ignore any update. I accepted that risk because the API has no edit endpoint.

**No link expiry.** Codes live forever. The alternative was to add a `expires_at` column and a scheduled job to prune expired rows from Postgres and Redis. I left it out because it adds operational complexity (the cleanup job, handling partial cache/DB consistency) and it wasn't in the core requirements. It is the first thing I would add.

---

## 4. What’s missing, or what you’d do with another day?

In priority order: **(1) link expiry** — a `TTL` field on creation, enforced at redirect time and cleaned up by a background job; **(2) click analytics** — a separate `visits` table recording timestamp and referrer per redirect, with a read endpoint to expose per-link stats; **(3) user accounts** — JWT-based auth so each user can manage their own links. I would also add Testcontainers to get end-to-end confidence on the Flyway schema and the Redis cache behaviour.
