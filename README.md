# Seat Reservation at Scale

Spring Boot + PostgreSQL service for atomic seat reservations under high concurrency.

## Live deployment

| | |
|---|---|
| **Base URL** | https://reserve-api-uma0.onrender.com |
| **Readiness** | https://reserve-api-uma0.onrender.com/readyz |
| **Metrics** | https://reserve-api-uma0.onrender.com/actuator/prometheus |
| **Logs** | Render dashboard → service `reserve-api` → Logs |

Render **free tier** may **cold-start** (30–60+ seconds) after idle time. If `/readyz` fails at first, wait and retry before running the burst script.

---

## Examiner: test the live service with `burst.sh`

**Prerequisites:** `bash`, `curl` (Git Bash on Windows, macOS, or Linux). Optional: `jq` for prettier JSON in script output.

### 1. Wake the service (cold start)

```bash
curl -sf https://reserve-api-uma0.onrender.com/readyz
```

Expected: `{"status":"UP"}`. Repeat every few seconds until it succeeds.

### 2. Run the one-command burst

From the repo root:

```bash
bash burst.sh https://reserve-api-uma0.onrender.com 200 500 32
```

Arguments:

| Arg | Example | Meaning |
|-----|---------|---------|
| 1 | `https://reserve-api-uma0.onrender.com` | API base URL (no trailing slash) |
| 2 | `200` | Number of distinct user tokens for the mixed load |
| 3 | `500` | Hot-seat storm size (concurrent attempts on seat **A12**) |
| 4 | `32` | Max parallel `curl` workers (lower if your machine is slow) |

The script:

1. Waits for `/readyz`
2. Mints an admin token and creates a **new** show (100 seats: A1–E20)
3. Runs a **hot-seat storm** on A12, **mixed** reserves on B1–B20, and **idempotent retries**
4. Prints HTTP outcome counts, checks **exactly one `201`** on the hot seat, **zero `5xx`**, and **reconciliation** via `GET /shows/{id}`
5. Prints a snapshot of Prometheus reservation metrics

**Success:** exit code `0` and final line `Burst completed successfully.`

**Smaller / faster run** (e.g. laptop or slow network):

```bash
bash burst.sh https://reserve-api-uma0.onrender.com 60 120 16
```

### 3. Manual smoke checks (optional)

```bash
BASE=https://reserve-api-uma0.onrender.com

# Readiness
curl -s "$BASE/readyz"

# Mint admin token (JSON uses snake_case)
ADMIN=$(curl -sf -X POST "$BASE/auth/token" \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"examiner-admin","role":"ADMIN","ttl_seconds":3600}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# Create a tiny show
curl -sf -X POST "$BASE/shows" \
  -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"smoke","seats":["Z1","Z2"],"price_paise":1000}'
```

### 4. What to verify

- Hot seat **A12:** exactly **one** `201`, remainder **`409`** (seat taken), **no `5xx`**
- `GET /shows/{id}`: `reconciled: true` and `available + held + confirmed == total`
- Prometheus: `reservations_declined_total{reason="seat_taken"}`, `reservations_held_total`, `seats_available` gauge

---

## Quick start (Docker, local)

```bash
docker compose up --build
```

API: `http://localhost:8080`

Local burst:

```bash
bash burst.sh http://127.0.0.1:8080 200 500 32
```

Use `127.0.0.1` instead of `localhost` on Windows if IPv6 causes connection issues.

## Local development

1. Start PostgreSQL:

```bash
docker compose up db
```

2. Run the app:

```bash
./gradlew bootRun
```

Environment (defaults in `application.properties`):

| Variable | Default |
|----------|---------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `reserve` |
| `SPRING_DATASOURCE_USERNAME` | `reserve` |
| `SPRING_DATASOURCE_PASSWORD` | `reserve` |
| `AUTH_HMAC_SECRET` | change in production |
| `HOLD_TTL_SECONDS` | `120` |
| `PER_USER_LIMIT` | `4` |

On Render, set `DB_*` and credentials via [`render.yaml`](render.yaml). Do **not** set `SPRING_DATASOURCE_URL` to a `postgres://` URL (Spring requires `jdbc:postgresql://…`).

## Auth

Mint a bearer token:

```bash
curl -s -X POST https://reserve-api-uma0.onrender.com/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"alice","role":"USER","ttl_seconds":3600}'
```

Admin role (`"role":"ADMIN"`) is required for `POST /shows`. Identity on reserve/cancel/confirm comes **only** from the token.

## API

- `POST /shows` — create show (admin)
- `GET /shows/{id}` — seat map + counts + reconciliation flag
- `POST /shows/{id}/reserve` — hold seats (all-or-nothing, idempotent)
- `POST /reservations/{id}/confirm` — confirm held seats
- `POST /reservations/{id}/cancel` — release hold (owner only)

Multi-seat reserve is **all-or-nothing**: if any seat is unavailable, the request returns **409** and no seats are held.

Idempotency: `Idempotency-Key` header or body `idempotency_key` (per user). Same key + same seats → replay original reservation; same key + different seats → **409**.

## Health & metrics

- Liveness: `GET /healthz`
- Readiness (DB checked): `GET /readyz`
- Prometheus: `GET /actuator/prometheus`

## Build & test

Gradle 9 requires **JDK 17+** to run the wrapper (app targets **Java 21**).

```bash
./gradlew build
```

Tests use Testcontainers (Docker required).

## Deploy (Render)

1. Push repo to GitHub.
2. Apply blueprint from [`render.yaml`](render.yaml) (web service + Postgres).
3. Ensure `AUTH_HMAC_SECRET` is set; link database env vars (`DB_HOST`, `DB_PORT`, `DB_NAME`, credentials).
4. **Live URL:** https://reserve-api-uma0.onrender.com

## Further reading

Design notes (atomicity, idempotency, holds, observability): [`WRITEUP.md`](WRITEUP.md).
