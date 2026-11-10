#!/usr/bin/env bash
# 실행 중인 서버의 핵심 API 흐름을 검증한다. 서버가 먼저 떠 있어야 한다.
# 사용법: ./scripts/smoke-test.sh [BASE_URL]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
checks=0

pass() { checks=$((checks + 1)); printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n      %s\n' "$1" "$2" >&2; exit 1; }

expect_status() { # <기대 코드> <설명> <응답 저장 파일> <curl 인자...>
  local want="$1" label="$2" out="$3" got
  shift 3
  got="$(curl -sS -o "$out" -w '%{http_code}' "$@")"
  [ "$got" = "$want" ] || fail "$label" "expected HTTP $want, got HTTP $got: $(cat "$out")"
  pass "$label"
}

json_get() { # <응답 파일> <키.경로>
  python3 -c '
import json, sys
doc = json.load(open(sys.argv[1]))
for key in sys.argv[2].split("."):
    doc = doc[key]
print(doc)
' "$1" "$2"
}

assert_list_has_id() { # <설명> <응답 파일> <키.경로> <id>
  python3 -c '
import json, sys
doc = json.load(open(sys.argv[1]))
for key in sys.argv[2].split("."):
    doc = doc[key]
sys.exit(0 if any(str(item["id"]) == sys.argv[3] for item in doc) else 1)
' "$2" "$3" "$4" || fail "$1" "$(cat "$2")"
  pass "$1"
}

curl -fsS -o /dev/null "$BASE/v0/spaces" \
  || fail "서버 연결" "$BASE 에 연결할 수 없습니다. ./gradlew bootRun 이 떠 있는지 확인하세요."

SUFFIX="$(date +%s)$$"
HOST_EMAIL="smoke.host.$SUFFIX@example.com"
RENTER_EMAIL="smoke.renter.$SUFFIX@example.com"
PASSWORD="smoketest1234"

read -r STARTS ENDS <<<"$(python3 -c '
from datetime import datetime, timedelta
start = (datetime.now() + timedelta(days=30)).replace(hour=10, minute=0, second=0, microsecond=0)
print(start.strftime("%Y-%m-%dT%H:%M:%S"), (start + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S"))
')"

cat > "$WORK/host-signup.json" <<JSON
{
  "email": "$HOST_EMAIL",
  "phone": "01012345678",
  "password": "$PASSWORD",
  "role": "HOST",
  "role_profile": { "nickname": "smoke-host", "bio": "smoke test host" }
}
JSON

cat > "$WORK/host-login.json" <<JSON
{ "email": "$HOST_EMAIL", "password": "$PASSWORD" }
JSON

cat > "$WORK/renter-signup.json" <<JSON
{
  "email": "$RENTER_EMAIL",
  "phone": "01087654321",
  "password": "$PASSWORD",
  "role": "RENTER",
  "role_profile": { "nickname": "smoke-renter", "bio": "smoke test renter" }
}
JSON

cat > "$WORK/renter-login.json" <<JSON
{ "email": "$RENTER_EMAIL", "password": "$PASSWORD" }
JSON

cat > "$WORK/space.json" <<'JSON'
{
  "category": "meeting",
  "name": "Smoke Test Meeting Room",
  "description": "created by scripts/smoke-test.sh",
  "is_open_24": true,
  "timezone": "Asia/Seoul",
  "is_visible": true,
  "is_closed_on_public_holidays": false
}
JSON

cat > "$WORK/subspace.json" <<'JSON'
{ "name": "Room A", "min_hours": 1, "max_hours": 4, "is_visible": true }
JSON

cat > "$WORK/cancel.json" <<'JSON'
{ "role": "RENTER", "cancellation_reason": "smoke test cancellation" }
JSON

expect_status 201 "HOST 회원가입" "$WORK/r01.json" \
  -X POST "$BASE/v0/users" -H 'Content-Type: application/json' -d @"$WORK/host-signup.json"

expect_status 200 "HOST 로그인 (세션 쿠키 저장)" "$WORK/r02.json" \
  -c "$WORK/host.jar" -X POST "$BASE/v0/login" -H 'Content-Type: application/json' -d @"$WORK/host-login.json"

expect_status 201 "공간 생성 (HOST)" "$WORK/r03.json" \
  -b "$WORK/host.jar" -X POST "$BASE/v0/spaces" -H 'Content-Type: application/json' -d @"$WORK/space.json"
SPACE_ID="$(json_get "$WORK/r03.json" data.id)"

expect_status 201 "서브스페이스 생성 (HOST)" "$WORK/r04.json" \
  -b "$WORK/host.jar" -X POST "$BASE/v0/spaces/$SPACE_ID/subspaces" \
  -H 'Content-Type: application/json' -d @"$WORK/subspace.json"
SUBSPACE_ID="$(json_get "$WORK/r04.json" data.id)"

expect_status 200 "공간 목록 (공개, category=meeting)" "$WORK/r05.json" \
  "$BASE/v0/spaces?category=meeting"
assert_list_has_id "목록에 생성한 공간이 포함됨" "$WORK/r05.json" data.spaces "$SPACE_ID"

expect_status 200 "공간 공개 상세 (공개)" "$WORK/r06.json" "$BASE/v0/spaces/$SPACE_ID"

expect_status 201 "RENTER 회원가입" "$WORK/r07.json" \
  -X POST "$BASE/v0/users" -H 'Content-Type: application/json' -d @"$WORK/renter-signup.json"

expect_status 200 "RENTER 로그인 (세션 쿠키 저장)" "$WORK/r08.json" \
  -c "$WORK/renter.jar" -X POST "$BASE/v0/login" -H 'Content-Type: application/json' -d @"$WORK/renter-login.json"

cat > "$WORK/reservation.json" <<JSON
{
  "subspace_id": $SUBSPACE_ID,
  "starts_at": "$STARTS",
  "ends_at": "$ENDS",
  "renter_name": "Smoke Renter",
  "renter_phone": "01087654321",
  "renter_email": "$RENTER_EMAIL",
  "custom_request": "smoke test"
}
JSON

expect_status 201 "예약 생성 (RENTER)" "$WORK/r09.json" \
  -b "$WORK/renter.jar" -X POST "$BASE/v0/reservation" \
  -H 'Content-Type: application/json' -d @"$WORK/reservation.json"
RESERVATION_ID="$(json_get "$WORK/r09.json" data.id)"

expect_status 409 "겹치는 예약은 409" "$WORK/r10.json" \
  -b "$WORK/renter.jar" -X POST "$BASE/v0/reservation" \
  -H 'Content-Type: application/json' -d @"$WORK/reservation.json"

expect_status 200 "예약 상세 (RENTER)" "$WORK/r11.json" \
  -b "$WORK/renter.jar" "$BASE/v0/reservation/$RESERVATION_ID"

expect_status 200 "예약 취소 (RENTER)" "$WORK/r12.json" \
  -b "$WORK/renter.jar" -X DELETE "$BASE/v0/reservation/$RESERVATION_ID" \
  -H 'Content-Type: application/json' -d @"$WORK/cancel.json"

expect_status 409 "이미 취소된 예약 재취소는 409" "$WORK/r13.json" \
  -b "$WORK/renter.jar" -X DELETE "$BASE/v0/reservation/$RESERVATION_ID" \
  -H 'Content-Type: application/json' -d @"$WORK/cancel.json"

expect_status 200 "호스트 예약 목록 (HOST)" "$WORK/r14.json" \
  -b "$WORK/host.jar" "$BASE/v0/host/reservations"

expect_status 401 "쿠키 없는 렌터 목록은 401" "$WORK/r15.json" \
  "$BASE/v0/renter/reservations"

printf '\n%d checks passed\n' "$checks"
