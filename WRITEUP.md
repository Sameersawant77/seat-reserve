# Write-up — Seat Reservation at Scale

## Atomic decision

**Mechanism:** PostgreSQL row locks + conditional `UPDATE`.

- **Single seat:** `SELECT … FOR UPDATE` on the seat row (via multi-seat path), then `UPDATE seats … WHERE status = 'available' OR (held AND expired)`. Exactly one concurrent transaction gets `UPDATE` row-count 1; others get 0 and return **409 seat taken** (never 500).
- **Multi-seat (all-or-nothing):** In one transaction, `SELECT … WHERE label IN (…) ORDER BY label FOR UPDATE` locks rows in **deterministic order** (alphabetical labels) so two transactions overlapping on different seat sets cannot deadlock. If any seat is not bookable, the transaction aborts with **409** and no partial holds.
- **Uniqueness:** `UNIQUE(show_id, label)` prevents duplicate seat rows.

**Why race-free:** The winner is chosen in the database by a single conditional write, not by read-then-write in application memory.

## Idempotency

- **Storage:** `idempotency_keys` with primary key `(user_id, key)`.
- **Enforcement:** `INSERT … ON CONFLICT DO NOTHING` at the start of the transaction. On conflict: if `request_hash` differs → **409**; if `completed` → return existing reservation (replay); if `in_progress` → **409** retry.
- **Hash:** SHA-256 of `show_id|sorted seat labels`.

## Holds & expiry

- Reserve creates **`held`** seats with `hold_expires_at = now() + hold_ttl_seconds`.
- **`@Scheduled` sweeper** clears expired `held` seats to `available` and marks reservations `expired` when no `held` seats remain.
- **`GET /shows/{id}`** treats expired holds as **available** in counts so `available + held + confirmed == total` holds between sweeps.
- **Cancel** only releases `held` seats owned by the caller; never touches `confirmed`.

## CAP under partition

With a single PostgreSQL primary, we favor **consistency** for seat assignment: reservations fail closed (409) rather than double-selling. If the app cannot reach the DB, **readiness** fails (`/readyz` → 503). During a network partition between app and DB, the service stops accepting traffic rather than guessing seat state.

## Observability (2am paging)

- **Page:** sustained `readyz` failures, DB connection pool exhaustion, any spike in **5xx** (should be zero in normal operation).
- **Warn:** `reservations_declined_total` seat_taken flatlines while traffic is high (possible logic bug), or `seats_available` gauge diverges from `GET /shows` counts.
- **Logs:** JSON with `correlationId` for tracing a reserve path end-to-end.

## AI usage

- **Directed:** Assignment breakdown, Render/Docker layout, Flyway schema sketch, burst script structure, README/WRITEUP outline.
- **Decided (human-owned depth):** All-or-nothing semantics, hold+confirm vs direct confirm, exact SQL locking order, idempotency state machine, effective seat status for reconciliation, metric names.
- **Implemented with AI assistance:** Boilerplate Spring structure, repository SQL, tests, and ops files — reviewed for race/idempotency alignment with the above.

## What next

- Payment integration with outbox + confirm-on-capture.
- Partitioned idempotency table or Redis cache for hot keys.
- Load test to 20k+ concurrency with connection pool tuning and PgBouncer.
- OpenAPI spec and admin dashboard for live show state.
