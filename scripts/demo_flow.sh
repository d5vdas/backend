#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8083"
TS=$(date +%s)
EMAIL="demo${TS}@example.com"
PASS="secret123"

echo "Using user: $EMAIL"

echo "[1] Register"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" \
  -d "{\"name\":\"Demo\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq

echo "[2] Login"
LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
echo "$LOGIN" | jq
TOKEN=$(echo "$LOGIN" | jq -r .token)

echo "[3] Start activity"
START=$(curl -s -X POST "$BASE_URL/activities/start" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"RUN"}')
echo "$START" | jq
ACTIVITY_ID=$(echo "$START" | jq -r .id)

echo "[4] Add points"
curl -s -X POST "$BASE_URL/activities/$ACTIVITY_ID/points" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"points":[{"latitude":12.9716,"longitude":77.5946,"recordedAt":"2026-03-01T12:20:00","sequenceNo":1},{"latitude":12.9720,"longitude":77.5950,"recordedAt":"2026-03-01T12:21:00","sequenceNo":2}]}' | jq

echo "[5] Stop activity"
curl -s -X POST "$BASE_URL/activities/$ACTIVITY_ID/stop" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"endedAt":"2026-03-01T12:30:00"}' | jq

echo "[6] List my activities"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/activities/me" | jq

echo "[7] Activity detail"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/activities/$ACTIVITY_ID" | jq

echo "[8] Social counts"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/social/activities/$ACTIVITY_ID/counts" | jq

echo "Done"
