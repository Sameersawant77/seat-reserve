CREATE TABLE shows (
    id              UUID PRIMARY KEY,
    name            TEXT NOT NULL,
    price_paise     BIGINT NOT NULL CHECK (price_paise >= 0),
    per_user_limit  INT NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
    hold_ttl_seconds INT NOT NULL DEFAULT 120 CHECK (hold_ttl_seconds > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reservations (
    id              UUID PRIMARY KEY,
    show_id         UUID NOT NULL REFERENCES shows(id),
    user_id         TEXT NOT NULL,
    status          TEXT NOT NULL CHECK (status IN ('held', 'confirmed', 'cancelled', 'expired')),
    amount_paise    BIGINT NOT NULL CHECK (amount_paise >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservations_show_user ON reservations(show_id, user_id);
CREATE INDEX idx_reservations_show_status ON reservations(show_id, status);

CREATE TABLE seats (
    id              UUID PRIMARY KEY,
    show_id         UUID NOT NULL REFERENCES shows(id),
    label           TEXT NOT NULL,
    status          TEXT NOT NULL CHECK (status IN ('available', 'held', 'confirmed')),
    reservation_id  UUID REFERENCES reservations(id),
    held_by         TEXT,
    hold_expires_at TIMESTAMPTZ,
    UNIQUE (show_id, label)
);

CREATE INDEX idx_seats_show_status ON seats(show_id, status);
CREATE INDEX idx_seats_show_label ON seats(show_id, label);

CREATE TABLE idempotency_keys (
    user_id         TEXT NOT NULL,
    key             TEXT NOT NULL,
    request_hash    TEXT NOT NULL,
    reservation_id  UUID REFERENCES reservations(id),
    status          TEXT NOT NULL CHECK (status IN ('in_progress', 'completed')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, key)
);
