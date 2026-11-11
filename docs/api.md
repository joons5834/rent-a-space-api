# API 레퍼런스

> 아래 모든 요청과 응답은 `main` @ `ab071f5`를 로컬에서 실행해 실제로 받은 것입니다
> (2026-11-10, Spring Boot 4.0.3, PostgreSQL 16). 이메일·id·시각은 실행 환경에 따라 달라집니다.
> 예시를 뜬 뒤의 변경은 CI의 `smoke` 잡(`.github/workflows/ci.yml`)이 `main` push와 pull request에서
> 같은 흐름을 실제 PostgreSQL 16에 다시 호출해 확인합니다. `main`이 아닌 브랜치에 PR 없이 올린 push는
> 이 잡이 돌지 않습니다. 아래 경로·상태 코드는 그 검증 대상입니다.

## 공통 규칙

- 기준 URL: `http://localhost:8080`. 모든 경로에 `/v0` 접두사가 붙습니다.
- 인증: 세션 쿠키(`JSESSIONID`). `POST /v0/login` 응답의 `Set-Cookie`를 이후 요청에 그대로 보냅니다.
  CSRF 토큰은 없습니다(비활성).
- 필드 이름: 요청·응답 모두 snake_case입니다.
- 시각: `starts_at`/`ends_at`은 공간 `timezone` 기준 로컬 시각(`yyyy-MM-ddTHH:mm:ss`)입니다.
  `created_at`/`reserved_at`은 UTC 오프셋을 포함한 ISO-8601입니다.
- 목록: `limit`(기본 10) + `next_cursor`. `next_cursor`가 `null`이면 마지막 페이지입니다.
- 인증 실패(`401`)는 본문이 비어 있습니다(`Content-Length: 0`).

## 오류 응답

애플리케이션이 직접 만든 오류는 `{"error": "메시지"}` 형태입니다. Bean Validation 실패만 필드 정보가 붙습니다.
필드 이름은 자바 프로퍼티 경로를 그대로 씁니다(`roleProfile.nickname`, `getUnavailableDates.year`).

```json
{"fieldErrors":{"password":"Password must be between 10 and 128 characters","roleProfile.nickname":"Nickname must be between 1 and 15 characters"},"error":"Validation failed","status":400}
```

| 상황 | 상태 | 응답 본문 |
|---|---|---|
| 이메일 중복 가입 | 409 | `{"error":"User with email docs.host@example.com already exists"}` |
| 자격 증명 불일치 | 401 | `{"error":"Invalid credentials"}` |
| 입력 검증 실패 | 400 | `{"error":"Validation failed","fieldErrors":{...},"status":400}` |
| 없는 카테고리 | 400 | `{"error":"Invalid category"}` |
| `images`에 없는 이미지 URL | 400 | `{"error":"Image not found: <url>"}` |
| 지난 시각으로 예약 | 400 | `{"error":"Reservation start time must be in the future"}` |
| 공간 운영 시간 밖 예약 | 409 | `{"error":"Space is closed during the requested time window"}` |
| 예약 시간 중복 | 409 | `{"error":"Time window overlaps with existing reservations"}` |
| 최소·최대 시간 위반 | 409 | `{"error":"Reservation duration exceeds maximum hours allowed: 2"}` |
| 비공개 서브스페이스 예약 | 409 | `{"error":"Subspace is not available for reservation"}` |
| 이미 취소된 예약 재취소 | 409 | `{"error":"Reservation already cancelled"}` |
| 공개 상태 서브스페이스 삭제 | 409 | `{"error":"The subspace is currently visible to the public."}` |
| 남의 예약 상세 조회 | 403 | `{"error":"Not permitted to see reservation detail."}` |
| 비공개·삭제된 서브스페이스 공개 조회 | 404 | `{"error":"Subspace not found with id: 2"}` |

`@PreAuthorize`에서 거부되면 애플리케이션 예외가 아니라 Spring Security 기본 응답이 나갑니다.

```json
{"timestamp":"2026-11-10T06:07:27.362Z","status":403,"error":"Forbidden","path":"/v0/subspaces/2"}
```

## 인증

### POST /v0/users — 회원가입

- 권한: 공개
- 요청 필드: `email`(필수, ≤254자), `phone`(선택, 10~15자), `password`(필수, 10~128자),
  `role`(`RENTER`/`HOST`/`ADMIN`), `role_profile.nickname`(1~15자), `role_profile.bio`(≤255자)
- 성공: `201`, 본문은 `UserResponse`(래핑 없음)
- 실패: `400` 검증 실패, `409` 이메일 중복

```bash
curl -i -X POST http://localhost:8080/v0/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"docs.host@example.com","phone":"01012345678","password":"doctest1234","role":"HOST","role_profile":{"nickname":"docs-host","bio":"documentation host"}}'
```

```json
{"id":1,"email":"docs.host@example.com","phone":"01012345678","enabled":true,"created_at":"2026-11-10T06:07:23.539520262Z","role":"HOST","role_profile":{"nickname":"docs-host","bio":"documentation host"}}
```

같은 이메일로 다시 요청하면 `409`:

```json
{"error":"User with email docs.host@example.com already exists"}
```

비밀번호가 10자 미만이고 닉네임이 15자를 넘으면 `400`:

```json
{"fieldErrors":{"password":"Password must be between 10 and 128 characters","roleProfile.nickname":"Nickname must be between 1 and 15 characters"},"error":"Validation failed","status":400}
```

### POST /v0/login — 로그인

- 권한: 공개
- 요청 필드: `email`, `password`
- 성공: `200`, `Set-Cookie: JSESSIONID=...; Path=/; HttpOnly`, 본문에 사용자와 **활성** 역할 목록
- 실패: `400` 검증 실패, `401` 자격 증명 불일치

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/v0/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"docs.host@example.com","password":"doctest1234"}'
```

```json
{"data":{"user":{"id":1,"email":"docs.host@example.com","roles":[{"role_name":"HOST","nickname":"docs-host"}]}}}
```

비밀번호가 틀리면 `401`:

```json
{"error":"Invalid credentials"}
```

### POST /v0/logout — 로그아웃

- 권한: 로그인
- 성공: `200`, 본문 없음. 세션이 무효화되어 같은 쿠키로 보호된 엔드포인트를 호출하면 `401`입니다.
- 실패: `401`

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/v0/logout
```

### POST /v0/users/{id}/roles — 역할 추가

- 권한: 본인 또는 ADMIN
- 요청 필드: `role`, `role_profile.nickname`(1~15자), `role_profile.bio`
- 성공: `201`, `UserResponse`
- 실패: `403` 권한 없음, `409 User already has role: <ROLE>`, `400` 검증 실패
- 주의: 본인에게 역할을 추가하면 세션 권한도 갱신되므로 `-c cookies.txt`로 쿠키를 다시 저장해야 합니다.

```bash
curl -i -b cookies.txt -c cookies.txt -X POST http://localhost:8080/v0/users/1/roles \
  -H 'Content-Type: application/json' \
  -d '{"role":"RENTER","role_profile":{"nickname":"host-renter","bio":null}}'
```

```json
{"id":1,"email":"docs.host@example.com","phone":"01012345678","enabled":true,"created_at":"2026-11-10T06:07:23.53952Z","role":"RENTER","role_profile":{"nickname":"host-renter","bio":null}}
```

같은 역할을 다시 추가하면 `409`:

```json
{"error":"User already has role: RENTER"}
```

## 공간

### POST /v0/spaces — 공간 생성

- 권한: HOST 또는 ADMIN (HOST 프로필이 활성 상태여야 함)
- 요청 필드: `category`(필수, `db/seed.sql`에 있는 이름), `name`(필수), `description`, `is_open_24`,
  `opens_at`/`closes_at`(`HH:mm:ss`), `main_image_url`, `images_urls`, `phone1`, `phone2`, `email`,
  `is_closed_on_public_holidays`, `closes_on_every`, `closes_on`, `is_visible`, `timezone`(예: `Asia/Seoul`)
- 성공: `201`, `SpaceResponse`. **`data.id`가 문자열로 나옵니다.**
- 실패: `400 Invalid category`, `400 Image not found: <url>`, `403` 권한 없음

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/v0/spaces \
  -H 'Content-Type: application/json' \
  -d '{"category":"meeting","name":"Docs Meeting Room","description":"공간 API 문서용 예시","is_open_24":false,"opens_at":"09:00:00","closes_at":"22:00:00","phone1":"0212345678","email":"docs.host@example.com","is_closed_on_public_holidays":true,"is_visible":true,"timezone":"Asia/Seoul"}'
```

```json
{"data":{"id":"1","category":"meeting","name":"Docs Meeting Room","description":"공간 API 문서용 예시","is_open_24":false,"opens_at":"09:00:00","closes_at":"22:00:00","main_image_url":null,"images_urls":[],"phone1":"0212345678","phone2":null,"email":"docs.host@example.com","is_closed_on_public_holidays":true,"closes_on_every":null,"closes_on":[],"is_visible":true,"timezone":"Asia/Seoul"}}
```

시드에 없는 카테고리를 보내면 `400`:

```json
{"error":"Invalid category"}
```

`images` 테이블에 없는 URL을 보내면 `400`:

```json
{"error":"Image not found: https://example.com/missing.png"}
```

### GET /v0/spaces — 공간 목록

- 권한: 공개
- 파라미터: `category`(정확히 일치하는 이름), `limit`(기본 10), `cursor`(이전 응답의 `next_cursor`)
- 성공: `200`
- 실패: `400 Illegal cursorId format.`(숫자가 아닌 커서)

```bash
curl -s 'http://localhost:8080/v0/spaces?category=meeting&limit=5'
```

```json
{"data":{"spaces":[{"id":1,"name":"Docs Meeting Room","category":"meeting","thumb_image_url":null}],"next_cursor":null}}
```

### GET /v0/spaces/{id} — 공간 공개 상세

- 권한: 공개
- 성공: `200`. 비공개 서브스페이스는 `subspaces` 목록에서 빠집니다.
- 실패: `404 Space not found with id: <id>`

```bash
curl -s http://localhost:8080/v0/spaces/1
```

```json
{"data":{"id":1,"name":"Docs Meeting Room","description":"공간 API 문서용 예시","images_urls":[],"timezone":"Asia/Seoul","subspaces":[]}}
```

위 응답은 서브스페이스를 만들기 전에 호출해 `subspaces`가 비어 있습니다. 서브스페이스를 만들면
`{"id":1,"name":"회의실 A"}` 같은 항목이 들어갑니다.

### GET /v0/host/spaces/{id} — 공간 관리용 상세

- 권한: 소유 HOST 또는 ADMIN
- 성공: `200`, `SpaceResponse`(생성 응답과 같은 필드 집합)
- 실패: `403`, `404`

```bash
curl -s -b cookies.txt http://localhost:8080/v0/host/spaces/1
```

### PATCH /v0/spaces/{id} — 공간 수정

- 권한: 소유 HOST 또는 ADMIN
- 요청 필드: 모두 선택(`UpdateSpaceRequest`). 빈 본문 `{}`은 `400`.
- 성공: `200`, 수정 후 `SpaceResponse`
- 실패: `400`(빈 본문), `403`, `404`

```bash
curl -s -b cookies.txt -X PATCH http://localhost:8080/v0/spaces/1 \
  -H 'Content-Type: application/json' -d '{"description":"설명을 수정했습니다."}'
```

```json
{"data":{"id":"1","category":"meeting","name":"Docs Meeting Room","description":"설명을 수정했습니다.","is_open_24":false,"opens_at":"09:00:00","closes_at":"22:00:00","main_image_url":null,"images_urls":[],"phone1":"0212345678","phone2":null,"email":"docs.host@example.com","is_closed_on_public_holidays":true,"closes_on_every":null,"closes_on":[],"is_visible":true,"timezone":"Asia/Seoul"}}
```

수정할 필드가 하나도 없으면 `400`이고 본문은 비어 있습니다.

## 서브스페이스

### POST /v0/spaces/{id}/subspaces — 서브스페이스 생성

- 권한: 소유 HOST 또는 ADMIN
- 요청 필드: `name`(필수), `description`, `main_image_url`, `images_urls`, `min_hours`, `max_hours`, `is_visible`
- 성공: `201`, `SubspaceResponse`
- 실패: `400`(이름 누락), `403`, `404`

```bash
curl -s -b cookies.txt -X POST http://localhost:8080/v0/spaces/1/subspaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"회의실 A","description":"6인실","min_hours":1,"max_hours":4,"is_visible":true}'
```

```json
{"data":{"id":1,"name":"회의실 A","description":"6인실","main_image_url":null,"images_urls":null,"min_hours":1,"max_hours":4,"is_visible":true}}
```

### GET /v0/host/subspaces/{id} — 서브스페이스 관리용 상세

- 권한: 소유 HOST 또는 ADMIN
- 성공: `200`

```bash
curl -s -b cookies.txt http://localhost:8080/v0/host/subspaces/1
```

```json
{"data":{"id":1,"name":"회의실 A","description":"6인실","main_image_url":null,"images_urls":null,"min_hours":1,"max_hours":4,"is_visible":true}}
```

### PATCH /v0/subspaces/{id} — 서브스페이스 수정

- 권한: 소유 HOST 또는 ADMIN
- 요청 필드: `name`, `description`, `main_image_url`, `images_urls`, `min_hours`, `max_hours`, `is_visible`
- 성공: `200`
- 실패: `400`(빈 본문), `403`, `404`

```bash
curl -s -b cookies.txt -X PATCH http://localhost:8080/v0/subspaces/1 \
  -H 'Content-Type: application/json' -d '{"max_hours":6}'
```

```json
{"data":{"id":1,"name":"회의실 A","description":"6인실","main_image_url":null,"images_urls":null,"min_hours":1,"max_hours":6,"is_visible":true}}
```

이미 삭제된 서브스페이스를 수정하면 소유권 검사에서 먼저 걸러져 `404`가 아니라 `403`이 나갑니다.

```json
{"timestamp":"2026-11-10T06:07:27.362Z","status":403,"error":"Forbidden","path":"/v0/subspaces/2"}
```

### GET /v0/subspaces/{id} — 서브스페이스 공개 상세

- 권한: 공개. `is_visible=false`이거나 soft delete된 서브스페이스는 `404`.
- 성공: `200`

```bash
curl -s http://localhost:8080/v0/subspaces/1
```

```json
{"data":{"id":1,"name":"회의실 A","description":"6인실","images_urls":[],"min_hours":1,"max_hours":6}}
```

### GET /v0/subspaces/{id}/unavailable-dates — 휴무일 조회

- 권한: 로그인
- 파라미터: `year`(1~9999), `month`(1~12)
- 성공: `200`, 해당 월의 휴무 일자 목록. 휴무 규칙이 없으면 빈 배열.
- 실패: `400` 검증 실패

```bash
curl -s -b cookies.txt 'http://localhost:8080/v0/subspaces/1/unavailable-dates?year=2026&month=12'
```

```json
{"data":{"unavailable_dates":[]}}
```

`year=0&month=13`으로 호출하면:

```json
{"fieldErrors":{"getUnavailableDates.year":"must be greater than or equal to 1","getUnavailableDates.month":"must be less than or equal to 12"},"error":"Validation failed","status":400}
```

### GET /v0/subspaces/{id}/unavailable-hours — 예약 불가 시간 조회

- 권한: 로그인
- 파라미터: `date`(`yyyy-MM-dd`)
- 성공: `200`, 공간 운영 시간 밖 구간과 기존 예약 구간
- 실패: `409`(공간 운영 시간 미설정), `404`

```bash
curl -s -b cookies.txt 'http://localhost:8080/v0/subspaces/1/unavailable-hours?date=2026-12-10'
```

```json
{"data":{"unavailable_hours":[{"start":"22:00:00","end":"09:00:00"}]}}
```

공간이 `09:00~22:00` 운영이라 `22:00~09:00`이 예약 불가 구간으로 나옵니다.

### DELETE /v0/subspaces/{id} — 서브스페이스 soft delete

- 권한: 소유 HOST 또는 ADMIN
- 성공: `200`, 본문 없음. `deleted_at`만 기록되고 행은 남습니다.
- 실패: `409`(공개 상태이거나 진행·예정 예약이 있음), `403`, `404`

```bash
curl -i -b cookies.txt -X DELETE http://localhost:8080/v0/subspaces/1
```

공개 중인 서브스페이스를 지우려 하면 `409`:

```json
{"error":"The subspace is currently visible to the public."}
```

`is_visible=false`로 바꾼 뒤 삭제하면 `200`입니다.

## 예약

### POST /v0/reservation — 예약 생성

- 권한: RENTER (RENTER 프로필이 활성 상태여야 함)
- 요청 필드: `subspace_id`, `starts_at`, `ends_at`(공간 timezone 기준 로컬 시각),
  `renter_name`, `renter_phone`, `renter_email`, `custom_request`
- 성공: `201`, 생성된 예약 id만 반환
- 실패: `400`(지난 시각, 검증 실패), `409`(중복, 운영 시간 밖, 최소·최대 시간 위반, 비공개), `404`(없는 서브스페이스)

```bash
curl -i -b renter-cookies.txt -X POST http://localhost:8080/v0/reservation \
  -H 'Content-Type: application/json' \
  -d '{"subspace_id":1,"starts_at":"2026-12-10T10:00:00","ends_at":"2026-12-10T12:00:00","renter_name":"김렌터","renter_phone":"01087654321","renter_email":"docs.renter@example.com","custom_request":"칠판이 필요합니다"}'
```

```json
{"data":{"id":1}}
```

같은 시간대를 다시 요청하면 `409`:

```json
{"error":"Time window overlaps with existing reservations"}
```

최대 2시간 서브스페이스에 3시간을 요청하면 `409`:

```json
{"error":"Reservation duration exceeds maximum hours allowed: 2"}
```

지난 시각을 요청하면 `400`:

```json
{"error":"Reservation start time must be in the future"}
```

비공개 서브스페이스에 예약하면 `409`:

```json
{"error":"Subspace is not available for reservation"}
```

### GET /v0/host/reservations — 호스트 예약 목록

- 권한: HOST
- 파라미터: `orderBy`(`id` 기본, `starts_at`), `status`(`PENDING`/`CONFIRMED`/`CANCELLED`), `cursor`, `limit`
- 성공: `200`. `orderBy=starts_at`일 때 `next_cursor`는 `<ISO 시각>|<id>` 형식입니다.
- 실패: `403`(활성 HOST 프로필 없음)

```bash
curl -s -b cookies.txt 'http://localhost:8080/v0/host/reservations?limit=5'
```

```json
{"reservations":[{"id":1,"timezone":"Asia/Seoul","starts_at":"2026-12-10T10:00:00","ends_at":"2026-12-10T12:00:00","status":"CONFIRMED","renter_name":"김렌터","subspace_name":"회의실 A","space_name":"Docs Meeting Room"}],"next_cursor":null}
```

### GET /v0/renter/reservations — 렌터 예약 목록

- 권한: RENTER, 파라미터는 호스트 목록과 같습니다.
- 성공: `200`

```bash
curl -s -b renter-cookies.txt 'http://localhost:8080/v0/renter/reservations?limit=5'
```

```json
{"reservations":[{"id":1,"timezone":"Asia/Seoul","starts_at":"2026-12-10T10:00:00","ends_at":"2026-12-10T12:00:00","status":"CONFIRMED","renter_name":"김렌터","subspace_name":"회의실 A","space_name":"Docs Meeting Room"}],"next_cursor":null}
```

쿠키 없이 호출하면 `401`이고 본문은 비어 있습니다.

### GET /v0/reservation/{id} — 예약 상세

- 권한: 해당 예약의 호스트·렌터 또는 ADMIN
- 성공: `200`
- 실패: `403`(관계없는 사용자), `404`

```bash
curl -s -b renter-cookies.txt http://localhost:8080/v0/reservation/1
```

```json
{"data":{"id":1,"status":"CONFIRMED","reserved_at":"2026-11-10T06:07:26.328324Z","space_name":"Docs Meeting Room","subspace_name":"회의실 A","timezone":"Asia/Seoul","starts_at":"2026-12-10T10:00:00","ends_at":"2026-12-10T12:00:00","custom_request":"칠판이 필요합니다","renter_name":"김렌터","renter_email":"docs.renter@example.com","renter_phone":"01087654321"}}
```

관계없는 렌터가 조회하면 `403`:

```json
{"error":"Not permitted to see reservation detail."}
```

### DELETE /v0/reservation/{id} — 예약 취소

- 권한: 해당 예약의 호스트·렌터
- 요청 필드: `role`(`HOST`/`RENTER`), `cancellation_reason`(필수, ≤254자)
- 성공: `200`, 본문 없음
- 실패: `409`(이미 취소됨, 이미 시작된 예약), `403`(소유자가 아님)

```bash
curl -i -b renter-cookies.txt -X DELETE http://localhost:8080/v0/reservation/1 \
  -H 'Content-Type: application/json' \
  -d '{"role":"RENTER","cancellation_reason":"일정이 변경되었습니다."}'
```

같은 예약을 다시 취소하면 `409`:

```json
{"error":"Reservation already cancelled"}
```
