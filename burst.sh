#!/usr/bin/env bash
# Load test: bash burst.sh [BASE_URL] [CONCURRENCY] [HOT_USERS] [MAX_PARALLEL]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
CONCURRENCY="${2:-200}"
HOT_USERS="${3:-500}"
MAX_PARALLEL="${4:-32}"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

json_field() {
  local json="$1"
  local key="$2"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r --arg k "$key" '.[$k] // .counts[$k] // empty'
    return
  fi
  printf '%s' "$json" | tr -d '\n' | sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p;t;s/.*\"${key}\"[[:space:]]*:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p"
}

wait_ready() {
  echo "Checking readiness at $BASE_URL/readyz ..."
  local i
  for i in $(seq 1 30); do
    if curl -sf "$BASE_URL/readyz" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Service not ready after 30 attempts" >&2
  return 1
}

mint_token() {
  local user="$1"
  local role="$2"
  local body="{\"user_id\":\"$user\",\"role\":\"$role\",\"ttl_seconds\":3600}"
  local resp
  resp="$(curl -sf -X POST "$BASE_URL/auth/token" -H 'Content-Type: application/json' -d "$body")"
  json_field "$resp" token
}

reserve_code() {
  local token="$1"
  local seat="$2"
  local key="$3"
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $key" \
    -d "{\"seats\":[\"$seat\"],\"idempotency_key\":\"$key\"}"
}

# Run a batch of backgrounded jobs, MAX_PARALLEL at a time, without polling.
# Usage: run_batch <count> <fn> ; fn is called with the 1-based index.
run_batch() {
  local count="$1"
  local fn="$2"
  local launched=0
  local idx
  for idx in $(seq 1 "$count"); do
    "$fn" "$idx" &
    launched=$((launched + 1))
    if (( launched % MAX_PARALLEL == 0 )); then
      wait
    fi
  done
  wait
}

wait_ready

ADMIN_TOKEN="$(mint_token admin ADMIN)"
SEATS_JSON=""
for row in A B C D E; do
  for col in $(seq 1 20); do
    if [[ -n "$SEATS_JSON" ]]; then SEATS_JSON+=","; fi
    SEATS_JSON+="\"${row}${col}\""
  done
done

SHOW_JSON="$(curl -sf -X POST "$BASE_URL/shows" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"burst-$(date +%s)\",\"seats\":[${SEATS_JSON}],\"price_paise\":25000}")"

SHOW_ID="$(json_field "$SHOW_JSON" id)"
echo "Created show $SHOW_ID with 100 seats"

echo "Minting $CONCURRENCY user tokens ..."
mint_one() { mint_token "user-$1" USER >"$tmpdir/token-$1"; }
run_batch "$CONCURRENCY" mint_one

: >"$tmpdir/codes.txt"
: >"$tmpdir/hot-codes.txt"

echo "Hot-seat storm on A12 ($HOT_USERS requests, parallel=$MAX_PARALLEL) ..."
hot_one() {
  local n="$1"
  local u=$(( (n - 1) % CONCURRENCY + 1 ))
  local token
  token="$(cat "$tmpdir/token-$u")"
  reserve_code "$token" "A12" "hot-A12-$n" >"$tmpdir/hot-$n.code"
}
run_batch "$HOT_USERS" hot_one
for f in "$tmpdir"/hot-*.code; do
  [[ -f "$f" ]] && cat "$f" >>"$tmpdir/hot-codes.txt"
done
rm -f "$tmpdir"/hot-*.code
cat "$tmpdir/hot-codes.txt" >>"$tmpdir/codes.txt"

echo "Mixed load ($CONCURRENCY requests) ..."
mix_one() {
  local i="$1"
  local token
  token="$(cat "$tmpdir/token-$i")"
  local seat="B$(( (i - 1) % 20 + 1 ))"
  reserve_code "$token" "$seat" "mix-$i-$seat" >"$tmpdir/mix-$i.code"
}
run_batch "$CONCURRENCY" mix_one
for f in "$tmpdir"/mix-*.code; do
  [[ -f "$f" ]] && cat "$f" >>"$tmpdir/codes.txt"
done
rm -f "$tmpdir"/mix-*.code

echo "Idempotent retries ..."
for i in $(seq 1 20); do
  token="$(cat "$tmpdir/token-$i")"
  key="idem-$i"
  reserve_code "$token" "C1" "$key" >>"$tmpdir/codes.txt"
  reserve_code "$token" "C1" "$key" >>"$tmpdir/codes.txt"
done

count_lines() {
  # Count matching lines; always emit a single integer and succeed.
  local n
  n="$(grep -cE "$1" "$2" 2>/dev/null)" || true
  printf '%s' "${n:-0}"
}

echo "Hot-seat outcomes:"
sort "$tmpdir/hot-codes.txt" | uniq -c | sort -k2 -n || true
hot_201="$(count_lines '^201$' "$tmpdir/hot-codes.txt")"
if [[ "$hot_201" -ne 1 ]]; then
  echo "ERROR: expected exactly 1 HTTP 201 for hot seat A12, got $hot_201" >&2
  exit 1
fi

echo "HTTP outcomes (all phases):"
sort "$tmpdir/codes.txt" | uniq -c | sort -k2 -n || true
five_xx="$(count_lines '^5[0-9]{2}$' "$tmpdir/codes.txt")"
echo "5xx: $five_xx"
if [[ "$five_xx" -ne 0 ]]; then
  echo "ERROR: expected zero 5xx, got $five_xx" >&2
  exit 1
fi

FINAL="$(curl -sf "$BASE_URL/shows/$SHOW_ID")"
if command -v jq >/dev/null 2>&1; then
  echo "Final counts: $(printf '%s' "$FINAL" | jq -c '.counts')"
  reconciled="$(printf '%s' "$FINAL" | jq -r '.reconciled')"
else
  available="$(json_field "$FINAL" available)"
  held="$(json_field "$FINAL" held)"
  confirmed="$(json_field "$FINAL" confirmed)"
  total_count="$(json_field "$FINAL" total)"
  sum=$((available + held + confirmed))
  echo "Final counts: available=$available held=$held confirmed=$confirmed total=$total_count"
  reconciled="false"
  [[ "$sum" -eq "$total_count" ]] && reconciled="true"
fi
echo "Reconciled: $reconciled"
if [[ "$reconciled" != "true" ]]; then
  exit 1
fi

echo "Prometheus snapshot (reservation metrics):"
curl -sf "$BASE_URL/actuator/prometheus" | grep -E 'reservations_|seats_available' || true

echo "Burst completed successfully."
