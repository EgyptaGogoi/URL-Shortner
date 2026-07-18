# URL Shortener

A production-ready URL shortening service built with Spring Boot 3, PostgreSQL, and Redis.

## Features

- Shorten any `http`/`https` URL to a 6-character Base62 code
- Optional custom alias (1–10 alphanumeric characters)
- Automatic deduplication — same long URL always returns the same code
- 301 permanent redirect
- Redis cache-aside (1-week TTL) to minimise database reads
- IP-based rate limiting (10 requests/minute per IP)
- SSRF protection — private, loopback, and link-local addresses are blocked
- OpenAPI/Swagger documentation at `/swagger-ui.html`

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven | 3.9 |
| Docker | 24 |
| Docker Compose | v2 |

> **Note:** The project has been tested on Java 25 (Homebrew) with `lombok 1.18.38` and `byte-buddy 1.17.5` overrides already in `pom.xml`.

---

## Quick Start (Docker)

The fastest way to run everything — app, PostgreSQL, and Redis — with one command:

```bash
# From the repo root
docker compose up --build
```

The service will be available at `http://localhost:8080` once all containers are healthy (~30 seconds on first build).

To stop and remove containers:

```bash
docker compose down
```

To also wipe the database volume:

```bash
docker compose down -v
```

---

## Run Locally (without Docker)

### 1. Start infrastructure

You still need PostgreSQL and Redis running. The quickest way is to start just those services:

```bash
docker compose up postgres redis
```

Or use your own local instances — see [Configuration](#configuration) to override the connection details.

### 2. Build and run the application

```bash
cd url-shortener
mvn spring-boot:run
```

The app starts on port `8080`.

---

## Run Tests

```bash
cd url-shortener
mvn test
```

All 52 tests run in ~3 seconds with no external dependencies — the service and controller tests use Mockito and MockMvc, so no database or Redis is needed.

The integration test (`UrlShortenerApplicationTests`) is `@Disabled` by default. To enable it, start the infrastructure first:

```bash
docker compose up postgres redis
# then in another terminal:
mvn test -Dspring.profiles.active=test
```

### Test coverage

| Test class | Tests | What it covers |
|---|---|---|
| `UrlShortenerServiceTest` | 21 | URL validation, code generation, dedup, caching, SSRF |
| `UrlShortenerControllerTest` | 17 | HTTP status codes, validation boundaries, rate limiting |
| `RateLimiterServiceTest` | 5 | Token bucket per IP, exhaustion, isolation between IPs |

---

## API Reference

### POST /api/shorten

Shorten a URL. Rate-limited to **10 requests per minute per IP**.

**Request**

```json
{
  "url": "https://www.example.com/some/very/long/path",
  "customAlias": "mylink"
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `url` | string | yes | `http`/`https` only, max 2048 chars, no private IPs |
| `customAlias` | string | **no** | 1–10 alphanumeric characters |

**Response — 201 Created**

```json
{
  "shortCode": "mylink",
  "shortUrl": "http://localhost:8080/mylink",
  "longUrl": "https://www.example.com/some/very/long/path"
}
```

**Error responses**

| Status | When |
|---|---|
| 400 | Invalid URL, bad JSON, or validation failure |
| 409 | Custom alias already taken |
| 429 | Rate limit exceeded |

---

### GET /{code}

Redirects to the original URL.

```
GET /mylink
→ 301 Location: https://www.example.com/some/very/long/path
```

| Status | When |
|---|---|
| 301 | Code found — browser/curl follows the redirect |
| 404 | Code not found |

> Test with curl to see the redirect without following it:
> ```bash
> curl -v http://localhost:8080/mylink
> ```

---

## API Documentation (Swagger UI)

Open `http://localhost:8080/swagger-ui.html` in your browser after starting the app.

The raw OpenAPI spec is available at `http://localhost:8080/v3/api-docs`.

> The GET `/{code}` endpoint is intentionally browse-only in Swagger UI — use curl or your browser to test redirects.

---

## Configuration

All settings can be overridden with environment variables:

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/urlshortener` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `APP_BASE_URL` | `http://localhost:8080` | Base URL prepended to short codes |

When running with Docker Compose, these are wired automatically between containers.

---

## Project Structure

```
url-shortener/
├── src/main/java/com/urlshortener/
│   ├── config/          # Redis, rate limiter, and OpenAPI beans
│   ├── controller/      # REST endpoints (POST /api/shorten, GET /{code})
│   ├── dto/             # Request/response/error DTOs with validation
│   ├── exception/       # Domain exceptions + GlobalExceptionHandler
│   ├── model/           # UrlMapping JPA entity
│   ├── repository/      # Spring Data JPA repository
│   └── service/         # UrlShortenerService, RateLimiterService
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/    # Flyway SQL migrations
├── src/test/java/       # Unit tests (no DB or Redis required)
├── Dockerfile           # Multi-stage build (Maven → JRE)
└── pom.xml
```

---

## Architecture

```
Client
  │
  ▼
UrlShortenerController
  │
  ├── RateLimiterService (Bucket4j, 10 req/min/IP)
  │
  └── UrlShortenerService
        ├── validateUrl()     — scheme check + SSRF block via InetAddress
        ├── sha256(url)       — fast dedup without full-text comparison
        ├── Redis GET         — cache-aside read (TTL: 7 days)
        ├── PostgreSQL        — fallback read / authoritative write
        └── Redis SET         — populate cache on miss
```

**Short code generation:** The database auto-increment ID is Base62-encoded and padded with random characters to 6 characters total. This avoids a separate sequence table while keeping codes short and collision-free.

**Deduplication:** A SHA-256 hash of the long URL is stored alongside each row and indexed. On each shorten request, the service looks up the hash first — only if no match is found does it insert a new row.
