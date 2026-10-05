#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
CONCURRENCY="${2:-200}"
HOT_USERS="${3:-500}"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

echo "Checking readiness at $BASE_URL/readyz ..."
curl -sf "$BASE_URL/readyz" >/dev/null

ADMIN_TOKEN="$(curl -sf -X POST "$BASE_URL/auth/token" \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"admin","role":"ADMIN","ttl_seconds":3600}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"

SEATS_JSON="$(python3 - <<'PY'
seats=[]
for row in "ABCDE":
  for col in range(1,21):
    seats.append(f'"{row}{col}"')
print(",".join(seats))
PY
)"

SHOW_JSON="$(curl -sf -X POST "$BASE_URL/shows" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"burst-$(date +%s)\",\"seats\":[${SEATS_JSON}],\"price_paise\":25000}")"

SHOW_ID="$(echo "$SHOW_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
echo "Created show $SHOW_ID with 100 seats"
export SHOW_ID

for i in $(seq 1 "$CONCURRENCY"); do
  curl -sf -X POST "$BASE_URL/auth/token" \
    -H 'Content-Type: application/json' \
    -d "{\"user_id\":\"user-$i\",\"role\":\"USER\",\"ttl_seconds\":3600}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])' \
    >"$tmpdir/token-$i"
done

: >"$tmpdir/codes.txt"

do_reserve() {
  local token="$1"
  local seat="$2"
  local key="$3"
  local out="$4"
  local code
  code="$(curl -s -o "$out" -w "%{http_code}" -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $key" \
    -d "{\"seats\":[\"$seat\"],\"idempotency_key\":\"$key\"}")"
  echo "$code" >>"$tmpdir/codes.txt"
}

export -f do_reserve
export BASE_URL tmpdir

echo "Hot-seat storm on A12 with $HOT_USERS attempts ..."
seq 1 "$HOT_USERS" | xargs -P 64 -I{} bash -c '
  u=$(( {} % '"$CONCURRENCY"' + 1 ))
  token=$(cat "$tmpdir/token-$u")
  do_reserve "$token" "A12" "hot-A12-{}" "$tmpdir/hot-{}"
'

echo "Mixed load ($CONCURRENCY requests) ..."
seq 1 "$CONCURRENCY" | xargs -P 32 -I{} bash -c '
  token=$(cat "$tmpdir/token-{}")
  seat="B$(( {} % 20 + 1 ))"
  do_reserve "$token" "$seat" "mix-{}-$seat" "$tmpdir/mix-{}"
'

echo "Idempotent retries ..."
for i in $(seq 1 20); do
  token="$(cat "$tmpdir/token-$i")"
  key="idem-$i"
  do_reserve "$token" "C1" "$key" "$tmpdir/idem-$i-a"
  do_reserve "$token" "C1" "$key" "$tmpdir/idem-$i-b"
done

python3 - "$tmpdir/codes.txt" <<'PY'
import sys
from collections import Counter
codes=Counter(line.strip() for line in open(sys.argv[1]))
print("HTTP outcomes:", dict(sorted(codes.items())))
print("5xx:", sum(v for k,v in codes.items() if k.startswith('5')))
PY

FINAL="$(curl -sf "$BASE_URL/shows/$SHOW_ID")"
echo "$FINAL" | python3 - <<'PY'
import json,sys
doc=json.load(sys.stdin)
c=doc["counts"]
total=c["available"]+c["held"]+c["confirmed"]
print("Final counts:", c)
print("Reconciled:", total==c["total"] and doc.get("reconciled", total==c["total"]))
PY

echo "Prometheus snapshot (reservation metrics):"
curl -sf "$BASE_URL/actuator/prometheus" | grep -E 'reservations_|seats_available' || true
