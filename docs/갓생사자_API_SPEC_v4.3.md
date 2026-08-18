# 갓생사자 API Specification v4.3

> **문서 버전** v4.3  
> **작성 기준일** 2026-08-18  
> **문서 상태** MVP 구현 기준 API 명세안  
> **대상** Frontend / Backend / AI Integration / QA / Codex CLI  
> **상위 제품 기준** `갓생사자_PRD_v2.1.md`  
> **공통 구현 기준** `project_common_prompt_v4.0.md`  
> **말투 기능 기준** `speech_style_system_SRS_v2.7.md`  
> **데이터베이스 기준** `갓생사자_backend_database_design_v1.8.md`  
> **참고 문서** 팀 초안 `API_SPEC_v3.1.md`  
> **v4.3 변경 요약** Avatar 계약을 확정했다. 온보딩에서 성장 트랙 4종 중 하나를 고정 선택하고 optional 얼굴 사진을 multipart로 받아 얼굴 reference에만 사용한다. 최초 설정에서 Stage 1/2/3 세트를 준비하고 현재 Story Stage에 맞는 PNG만 인증된 이미지 Endpoint로 제공한다. 250×500 투명 PNG, host disk asset set 저장, 원본 사진 즉시 삭제, 온보딩 재생성 1회, 초기 생성 실패 시 기본 Stage 세트 fallback, Item 여러 개의 프론트 정적 PNG overlay를 반영했다. 별도 AvatarGenerationJob/asset table은 MVP에 추가하지 않는다.  
> **v4.2 변경 요약** Routine category 5종, TO_DO=ONCE, 카테고리별 사전 추천 API, 완료 Routine Point Claim API(PHOTO 10/CHECK 5, serviceDate당 최대 3개, 당일 한정), 누적 Point 100P Item unlock, 월간 Point 경쟁 Ranking을 반영했다. Verification 성공 응답에서 Point/Item 자동 지급을 제거하고 Point Claim 시 Item milestone을 처리한다. Point 소비/차감 및 Battle Pass API는 만들지 않는다.  

---

# 0. 문서 목적

이 문서는 2026-08-21 MVP 시연에 필요한 백엔드 HTTP API 계약을 정의한다.

설계 우선순위는 다음과 같다.

```text
최신 제품 요구사항과 일치
→ DB Source of Truth와 일치
→ 프론트에서 핵심 흐름을 끊김 없이 구현 가능
→ 중복 인증/해금과 시간 경계에서 데이터 무결성 유지
→ AI 장애 시 대체 흐름 제공
→ 해커톤 일정에 맞는 최소 복잡도
```

팀 초안 v3.1의 다음 장점은 유지했다.

- `/api/v1` prefix
- `data` 기반 공통 성공 응답
- 구조화된 공통 오류 응답
- 말투 분석 Job 방식
- PHOTO / CHECK 인증 Endpoint 분리
- Routine / Record / Story / Item 도메인 분리

다음 항목은 최신 기준과 충돌하여 재설계했다.

- 일반 Entity ID를 UUID로 고정하지 않음
- 존재하지 않는 인트로 Story 도메인 제거
- 평행세계 전용 Avatar/Story API 제거
- Story를 EP.1~EP.5, 10/20/30/40/50일 기준으로 갱신
- `DailyRoutine.status`를 DB 상태처럼 다루지 않음
- 전역 `Idempotency-Key` 저장 계약 제거
- Experience / XP / Coin / Shop / Item purchase 제거 유지
- Point는 소비형 재화가 아닌 수령 누적 점수로 추가
- 게스트 생성과 닉네임 설정 분리
- 종료 시각 이후 인증 차단 규칙 반영
- 누적 하루 성공일 기반 Item Unlock 제거, 누적 획득 Point 100P 기반 Item Unlock 반영

---

# 1. 핵심 사용자 흐름과 API

```text
POST /sessions
→ PATCH /users/me
→ PUT /avatars/me
→ POST /speech-style/preset
   또는 Kakao 분석 Job
→ GET /home

→ GET /routine-recommendations?category=...
→ POST /routines (category + repeatType/ONCE)
→ GET /daily-routines
→ POST /daily-routines/{id}/photo-mission
→ POST PHOTO 또는 CHECK verification
→ 완료 Routine에서 POST point-claim

→ Verification 내부에서
   DailySuccessRecord
   → Story Unlock

→ Point Claim 내부에서
   누적 Point
   → 100P Item Unlock
   → Avatar Stage 계산

→ GET /records
→ GET /stories
→ GET /items
```

MVP에서는 다음 API를 만들지 않는다.

```text
회원가입
로그인
OAuth
Demo mode
Demo clone
Coin
Shop
Item purchase
XP reward
Point spend / Point transfer
Battle Pass
인증 사진 조회
Story branch
AI Story generation
```

---

# 2. 공통 규칙

## 2.1 Base URL

```text
Local       http://localhost:8080/api/v1
Development 환경 변수/배포 설정에서 확정
Production  환경 변수/배포 설정에서 확정
```

API 문서와 소스 코드에 실제 Production Host를 하드코딩하지 않는다.

---

## 2.2 인증

MVP는 정식 회원가입/로그인을 사용하지 않는다.

```text
POST /sessions
→ 일반 Guest User 생성
→ opaque access token 발급
→ DB에는 token hash만 저장
```

인증이 필요한 요청:

```http
Authorization: Bearer <opaque-access-token>
```

토큰 payload를 클라이언트가 해석한다는 가정을 하지 않는다.

클라이언트가 다음 값을 보내더라도 권한 판단에 사용하지 않는다.

```text
userId
ownerId
guestId
```

리소스 소유자는 항상 인증 세션의 User로 판정한다.

---

## 2.3 ID 타입

DB Design v1.8를 기준으로 한다.

```text
User / Avatar / Routine / DailyRoutine
Verification / Item / StoryEpisode 등 일반 Entity
→ JSON number (BIGINT)

SpeechAnalysisJob
GuestSession 내부 ID
→ UUID string
```

API에서 DB PK 생성 전략을 UUID로 다시 강제하지 않는다.

예:

```json
{
  "routineId": 101,
  "dailyRoutineId": 405,
  "jobId": "5bfef1b4-2be3-4ea0-9c3f-e43547799d07"
}
```

---

## 2.4 날짜·시간

서비스 기본 시간대:

```text
Asia/Seoul
```

형식:

```text
serviceDate → YYYY-MM-DD
startTime/endTime → HH:mm
absolute timestamp → ISO 8601 with offset
```

### 루틴 시간 범위

MVP는 자정 넘김을 지원하지 않는다.

```text
startTime < endTime
```

만 허용한다.

```text
07:00~09:00  허용
22:00~23:59  허용
23:00~01:00  거부
09:00~09:00  거부
24:00         거부
```

입력 정밀도는 `HH:mm`이며 최대 시각은 `23:59`다.

서버 내부 판정은 종료 분 전체를 포함하기 위해:

```text
actualStartAt
= serviceDate + startTime

actualEndAtExclusive
= serviceDate + endTime + 1분
```

을 사용한다.

00:00 이후 전날 DailyRoutine은 오늘 Home/오늘 루틴 목록에 노출하지 않는다.

---

## 2.5 공통 성공 응답

```json
{
  "data": {}
}
```

목록에서 필요할 때만 `meta`를 사용한다.

```json
{
  "data": [],
  "meta": {
    "count": 10
  }
}
```

---

## 2.6 공통 오류 응답

```json
{
  "code": "VALIDATION_ERROR",
  "message": "요청값을 확인해 주세요.",
  "details": [
    {
      "field": "content",
      "reason": "비어 있을 수 없습니다."
    }
  ],
  "traceId": "01K2RA0A1M4M7H5N"
}
```

`message`는 사용자 화면에 표시 가능한 문장으로 작성한다.

OpenAI Response, stack trace, DB 오류 전문은 사용자에게 노출하지 않는다.

---

## 2.7 공통 HTTP Status

| Status | 사용 |
|---:|---|
| 200 | 조회/수정/도메인 처리 성공 |
| 201 | 새 Resource 생성 |
| 202 | 비동기 분석 Job 시작 |
| 204 | 반환 본문 없는 삭제 성공 |
| 400 | 형식/길이/필수값 검증 실패 |
| 401 | Guest token 없음/무효 |
| 404 | Resource 없음 또는 타 사용자 Resource |
| 409 | 현재 상태와 충돌, 중복 성공 처리 |
| 410 | 만료된 임시 분석 Job |
| 413 | 업로드 크기 초과 |
| 415 | 파일 형식 오류 |
| 422 | 형식은 맞지만 도메인 조건 실패 |
| 429 | 과도한 요청 |
| 500 | 서버 내부 오류 |
| 503 | OpenAI 등 외부 서비스 장애 |

---

# 3. 공통 Domain Error Code

## 사용자/온보딩

```text
UNAUTHORIZED
ONBOARDING_INCOMPLETE
NICKNAME_REQUIRED
AVATAR_NOT_CONFIGURED
SPEECH_STYLE_NOT_CONFIGURED
AVATAR_TRACK_REQUIRED
AVATAR_TRACK_LOCKED
AVATAR_FACE_PHOTO_INVALID
AVATAR_REGENERATION_LIMIT_REACHED
AVATAR_IMAGE_NOT_FOUND
AVATAR_GENERATION_FAILED
```

## Routine

```text
ROUTINE_NOT_FOUND
DAILY_ROUTINE_NOT_FOUND
INVALID_REPEAT_DAYS
INVALID_TIME_RANGE
VERIFICATION_OBJECT_NOT_SUPPORTED
SERVICE_DATE_LOCKED
INVALID_ROUTINE_CATEGORY
INVALID_REPEAT_TYPE_FOR_CATEGORY
INVALID_ONCE_DATE
```

## Verification

```text
ROUTINE_NOT_STARTED
ROUTINE_WINDOW_CLOSED
ALREADY_VERIFIED
PHOTO_MISSION_NOT_PREPARED
PHOTO_VERIFICATION_FAILED
PHOTO_NOT_DECIDABLE
PHOTO_AI_UNAVAILABLE
ROUTINE_NOT_COMPLETED
POINT_ALREADY_CLAIMED
POINT_CLAIM_LIMIT_REACHED
POINT_CLAIM_EXPIRED
```

## Speech

```text
STYLE_NOT_FOUND
PRESET_NOT_FOUND
INVALID_FILE_TYPE
ZIP_TOO_LARGE
UNSUPPORTED_ARCHIVE
CHAT_TEXT_NOT_FOUND
CHAT_FORMAT_UNSUPPORTED
PARTICIPANT_NOT_FOUND
INSUFFICIENT_MESSAGES
AI_ANALYSIS_FAILED
AI_RESPONSE_INVALID
DIALOGUE_GENERATION_FAILED
ANALYSIS_JOB_NOT_FOUND
ANALYSIS_EXPIRED
```

## Item/Story

```text
ITEM_NOT_FOUND
ITEM_NOT_OWNED
INVALID_EQUIPMENT
```

---

# 4. DailyRoutine API 상태값

DB에는 `DailyRoutine.status`를 저장하지 않는다.

API가 조회 시 다음과 같이 파생한다.

```text
Verification 존재
→ COMPLETED

Verification 없음
AND now < actualStartAt
→ UPCOMING

Verification 없음
AND actualStartAt <= now < actualEndAtExclusive
→ AVAILABLE

Verification 없음
AND now >= actualEndAtExclusive
→ FAILED
```

API Enum:

```text
UPCOMING
AVAILABLE
COMPLETED
FAILED
```

`PENDING`은 외부 API 상태값으로 사용하지 않는다.

---

# 5. 하루 상태 API 값

날짜 단위 상태는 다음으로 반환한다.

```text
NO_ROUTINE
IN_PROGRESS
SUCCESS
FAILED
```

판정:

```text
DailyRoutine 0개
→ NO_ROUTINE

1개 이상 존재
AND DailySuccessRecord 존재
→ SUCCESS

1개 이상 존재
AND 종료된 미인증 DailyRoutine 존재
→ FAILED

그 외
→ IN_PROGRESS
```

---

# 6. API 목록

| Domain | Method | Endpoint | 설명 |
|---|---|---|---|
| Infra | GET | `/health` | 배포 상태 확인 |
| Session | POST | `/sessions` | 일반 Guest 세션 생성 |
| User | GET | `/users/me` | 온보딩/사용자 상태 조회 |
| User | PATCH | `/users/me` | 닉네임 설정/수정 |
| Avatar | GET | `/avatars/me` | 현재 Avatar 상태 조회 |
| Avatar | PUT | `/avatars/me` | Avatar 최초 생성(Stage 1/2/3 세트 준비) |
| Avatar | GET | `/avatars/me/image` | 현재 Story Stage의 Avatar PNG 조회 |
| Avatar | POST | `/avatars/me/regenerate` | 온보딩 Avatar 세트 1회 재생성 |
| Avatar | POST | `/avatar-dialogues/selections` | 상황별 사전 대사 1개 선택 |
| Home | GET | `/home` | 홈 종합 상태 조회 |
| Speech | GET | `/speech-style/presets` | 프리셋 목록 |
| Speech | GET | `/speech-style` | 현재 활성 말투 조회 |
| Speech | POST | `/speech-style/preset` | 프리셋 활성화/전환 |
| Speech | POST | `/speech-style/kakao/jobs` | Kakao ZIP 업로드·참여자 추출 |
| Speech | POST | `/speech-style/kakao/jobs/{jobId}/analyze` | 본인 화자 선택·분석 시작 |
| Speech | GET | `/speech-style/kakao/jobs/{jobId}` | 분석 Job 상태 조회 |
| Speech | PATCH | `/speech-style` | 말투 옵션 수정·대사 재생성 |
| Speech | DELETE | `/speech-style` | 말투 초기화 |
| Routine | GET | `/verification-objects` | 지원 인증 물건 조회 |
| Routine | GET | `/routine-recommendations?category=` | 카테고리별 사전 추천 Routine 3개 |
| Routine | GET | `/routines` | 현재 활성 반복 Routine 목록 |
| Routine | GET | `/routines/{routineId}` | 반복 Routine 상세 |
| Routine | POST | `/routines` | Routine 생성 |
| Routine | PATCH | `/routines/{routineId}` | Routine 수정 |
| Routine | DELETE | `/routines/{routineId}` | Routine 삭제 |
| Daily | GET | `/daily-routines?date=YYYY-MM-DD` | 날짜별 DailyRoutine 조회 |
| Mission | POST | `/daily-routines/{dailyRoutineId}/photo-mission` | Photo Mission 준비/조회 |
| Verify | POST | `/daily-routines/{dailyRoutineId}/verifications/photo` | PHOTO 인증 |
| Verify | POST | `/daily-routines/{dailyRoutineId}/verifications/check` | CHECK 인증 |
| Point | POST | `/daily-routines/{dailyRoutineId}/point-claim` | 완료 Routine의 당일 Point 수령 |
| Record | GET | `/records?fromDate=&toDate=` | 기간별 기록 |
| Story | GET | `/stories` | Story 진행도와 EP.1~EP.5 |
| Item | GET | `/items` | 도감/보유/착용 상태 |
| Item | PUT | `/avatars/me/equipment` | 보유 Item 장착 상태 변경 |
| Competition | GET | `/competition/leaderboard?month=YYYY-MM` | 월간 획득 Point 순위 |

---

# 7. Infra API

## 7.1 Health Check

```http
GET /api/v1/health
```

인증 불필요.

### 200

```json
{
  "data": {
    "status": "UP"
  }
}
```

DB/OpenAI를 실제로 호출하는 무거운 health check로 만들지 않는다.

---

# 8. Session / User API

## 8.1 Guest Session 생성

```http
POST /api/v1/sessions
```

인증 불필요.

Request Body 없음.

### 201

```json
{
  "data": {
    "accessToken": "guest_8U1p...",
    "expiresAt": null,
    "user": {
      "id": 1001,
      "nickname": null
    },
    "nextStep": "NICKNAME_SETUP"
  }
}
```

### 정책

- 일반 Guest User와 GuestSession을 생성한다.
- access token 원문은 응답 시 한 번만 클라이언트에 전달한다.
- DB에는 hash만 저장한다.
- 별도 Demo mode를 만들지 않는다.
- 이미 유효 Token을 가진 클라이언트는 새 Session을 만들지 말고 `/users/me`를 호출한다.

### 테스트

```text
새 세션 생성
token hash 저장
raw token DB 미저장
Demo 관련 field 없음
과도한 생성 요청 429 가능
```

---

## 8.2 현재 사용자 조회

```http
GET /api/v1/users/me
Authorization: Bearer <token>
```

### 200

```json
{
  "data": {
    "id": 1001,
    "nickname": "김멋사",
    "avatarConfigured": true,
    "speechStyleConfigured": true,
    "nextStep": "HOME",
    "createdAt": "2026-08-17T18:20:00+09:00"
  }
}
```

`nextStep`:

```text
nickname 없음          → NICKNAME_SETUP
nickname 있음/avatar 없음 → AVATAR_SETUP
avatar 있음/style 없음 → SPEECH_STYLE_SETUP
모두 완료             → HOME
```

인트로 Story를 온보딩 단계로 추가하지 않는다.

---

## 8.3 닉네임 설정/수정

```http
PATCH /api/v1/users/me
Authorization: Bearer <token>
Content-Type: application/json
```

### Request

```json
{
  "nickname": "김멋사"
}
```

검증:

```text
trim 후 1~30자
```

### 200

```json
{
  "data": {
    "id": 1001,
    "nickname": "김멋사",
    "nextStep": "AVATAR_SETUP",
    "updatedAt": "2026-08-17T18:22:00+09:00"
  }
}
```

---

# 9. Avatar API

## 9.1 현재 Avatar 조회

```http
GET /api/v1/avatars/me
Authorization: Bearer <token>
```

### 200

```json
{
  "data": {
    "id": 12,
    "growthTrack": "SKIN",
    "stage": 2,
    "highestUnlockedEpisodeNumber": 1,
    "imageEndpoint": "/api/v1/avatars/me/image",
    "assetSource": "GENERATED",
    "regenerationRemaining": 1,
    "equippedItems": [
      {
        "itemId": 31,
        "type": "ACCESSORY",
        "assetKey": "items/accessory/31"
      }
    ],
    "updatedAt": "2026-08-18T22:00:00+09:00"
  }
}
```

### 중요

`stage`는 Avatar DB 컬럼이 아니다.

```text
Story 해금 없음 → 1
해금 Story avatarStage MAX → 현재 Stage
```

`imageEndpoint`는 **현재 Stage 이미지 하나만** 제공한다. Stage 2/3 파일이 서버에 미리 있어도 Story 해금 전에는 해당 미래 Stage asset 경로나 binary를 API에서 노출하지 않는다.

`equippedItems`는 `UserItem.equipped=true`에서 계산한다. Item actual PNG는 프론트 정적 asset이며 `assetKey`로 매핑한다.

---

## 9.2 Avatar 최초 생성

```http
PUT /api/v1/avatars/me
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Form:

```text
growthTrack = SKIN | WELL_BEING | HEALTH_FIT | DIET
facePhoto   = optional image/jpeg or image/png
```

`TO_DO`는 Avatar 성장 트랙으로 받을 수 없다.

### 얼굴 사진 정책

사진은 선택 사항이다.

사진을 제공하면:

- 한 명의 얼굴이어야 한다.
- 정면 또는 정면에 가까워야 한다.
- 얼굴이 충분히 보여야 한다.
- 얼굴 대부분이 가려진 입력은 거부할 수 있다.
- 사진은 캐릭터의 얼굴 특징 reference에만 사용한다.
- 실제 피부 상태, 안색, 붓기, 건강 상태를 분석해 Stage를 결정하지 않는다.
- 몸, 포즈, 체형은 고정 Template을 유지한다.

사진이 없으면 디자이너 기본 얼굴 Template을 사용한다.

형식/크기 검증 실패는 OpenAI 호출 전에 거부한다. 얼굴 조건을 만족하지 못하면:

```text
422 AVATAR_FACE_PHOTO_INVALID
```

사용자는 다른 사진을 선택하거나 사진 없이 계속할 수 있다.

### 생성 순서

MVP에서는 별도 `AvatarGenerationJob` API를 만들지 않고 **한 요청에서 세트를 준비하는 동기 흐름**을 기본안으로 한다. 프론트는 생성 중 로딩 상태를 표시한다.

```text
1. growthTrack 검증
2. optional facePhoto 임시 확보
3. 입력 사진 검증
4. 고정 Template + track + optional face reference로 Stage 1 생성
5. Stage 1을 reference로 Stage 2 생성
6. Stage 1을 reference로 Stage 3 생성
7. 실패한 이미지 생성은 자동 1회 재시도
8. 최종 이미지를 250×500 RGBA transparent PNG로 정규화
9. 임시 asset set directory에 Stage 1/2/3 저장
10. 세 장 모두 준비되면 Avatar row와 active asset_set_key 반영
11. finally facePhoto 삭제
```

Stage 2/3은 Stage 1이 준비된 뒤 서로 독립적으로 생성할 수 있다. 구현에서 안전하면 두 호출을 병렬화할 수 있다.

외부 AI 호출을 DB Transaction 안에서 실행하지 않는다.

### 초기 생성 fallback

AI 생성이 자동 재시도 후에도 최종 실패하면:

```text
선택한 growthTrack의 기본 Stage 1/2/3 asset set 연결
→ assetSource = DEFAULT
→ 200 성공
→ 온보딩 계속
```

AI 이미지 생성 실패 때문에 사용자를 온보딩에 묶어두지 않는다.

### 200

```json
{
  "data": {
    "id": 12,
    "created": true,
    "growthTrack": "SKIN",
    "stage": 1,
    "imageEndpoint": "/api/v1/avatars/me/image",
    "assetSource": "GENERATED",
    "fallbackUsed": false,
    "regenerationRemaining": 1,
    "nextStep": "SPEECH_STYLE_SETUP"
  }
}
```

Avatar가 이미 있고 growthTrack 변경을 시도하면:

```text
409 AVATAR_TRACK_LOCKED
```

성장 트랙은 MVP에서 변경하지 않는다.

### 원본 사진 삭제

서버는 사용자 원본 얼굴 사진을 장기 보관하지 않는다.

```text
성공
실패
fallback
client disconnect 등 처리 가능한 종료 경로
```

모두 `finally` cleanup 대상이다.

---

## 9.3 현재 Stage 이미지 조회

```http
GET /api/v1/avatars/me/image
Authorization: Bearer <token>
```

Response:

```http
200 OK
Content-Type: image/png
Cache-Control: private, max-age=...
```

처리:

```text
인증 User의 Avatar 조회
→ Story에서 currentStage 계산
→ asset_set_key + stage{currentStage}.png resolve
→ PNG stream
```

서버 host의 절대 경로를 API에 노출하지 않는다.

Bearer 인증을 사용하는 프론트는 필요하면 `fetch`로 PNG Blob을 받은 뒤 Object URL로 표시한다.

파일이 없는 비정상 상태:

```text
404 AVATAR_IMAGE_NOT_FOUND
```

---

## 9.4 Avatar 1회 재생성

```http
POST /api/v1/avatars/me/regenerate
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Form:

```text
facePhoto = optional image/jpeg or image/png
```

`growthTrack`은 request에서 변경하지 않는다. 기존 Avatar의 고정 `growthTrack`을 사용한다.

재생성 규칙:

- 온보딩에서 성공한 재생성 1회만 허용한다.
- Stage 1/2/3 전체를 새 세트로 생성한다.
- 새 세트가 모두 성공하기 전까지 기존 `asset_set_key`를 유지한다.
- 새 세트 성공 후 짧은 Transaction에서 active set을 교체한다.
- commit 후 이전 사용자 생성 파일 세트를 삭제한다.
- 새 세트 생성 실패 시 기존 세트를 유지한다.
- 서버는 초기 원본 얼굴 사진을 보관하지 않으므로 같은 얼굴을 사용하려면 프론트가 파일을 다시 전송해야 한다.
- 요청에 사용한 원본 사진은 처리 종료 후 다시 삭제한다.

이미 성공한 재생성을 사용했다면:

```text
409 AVATAR_REGENERATION_LIMIT_REACHED
```

AI 호출이 자동 재시도 후에도 실패한 경우 기존 Avatar는 그대로 사용 가능하므로:

```text
503 AVATAR_GENERATION_FAILED
```

로 응답할 수 있다. 기존 세트를 fallback 기본 이미지로 강제 교체하지 않는다.

### 200

```json
{
  "data": {
    "id": 12,
    "growthTrack": "SKIN",
    "stage": 1,
    "imageEndpoint": "/api/v1/avatars/me/image",
    "assetSource": "GENERATED",
    "regenerationRemaining": 0,
    "replaced": true
  }
}
```

---

## 9.5 상황별 Avatar Dialogue 선택

```http
POST /api/v1/avatar-dialogues/selections
```

### Request

```json
{
  "situation": "ROUTINE_AVAILABLE"
}
```

허용 Enum:

```text
ROUTINE_UPCOMING
ROUTINE_AVAILABLE
ROUTINE_REMINDER
ROUTINE_COMPLETED
ALL_COMPLETED
STREAK_CONTINUED
STREAK_BROKEN
RETURN_AFTER_ABSENCE
```

### 200

```json
{
  "data": {
    "dialogueId": 550,
    "situation": "ROUTINE_AVAILABLE",
    "content": "지금 하면 딱 맞겠다. 하나 하고 오자."
  }
}
```

조회 시 `lastUsedAt`, `useCount`를 갱신할 수 있으므로 GET이 아니라 POST를 사용한다.

말투가 설정되지 않은 사용자는:

```text
409 SPEECH_STYLE_NOT_CONFIGURED
```

---

# 10. Home API

## 10.1 홈 종합 상태

```http
GET /api/v1/home
```

홈은 항상 서버 기준 현재 시각을 사용한다.

과거 기록은 `/records`에서 조회한다.

### 200 예시

```json
{
  "data": {
    "serviceDate": "2026-08-17",
    "serverNow": "2026-08-17T18:40:00+09:00",
    "avatar": {
      "id": 12,
      "growthTrack": "SKIN",
      "stage": 2,
      "imageEndpoint": "/api/v1/avatars/me/image",
      "equippedItems": []
    },
    "progress": {
      "completedCount": 2,
      "totalCount": 3,
      "percentage": 67,
      "dayStatus": "IN_PROGRESS"
    },
    "points": {
      "totalEarned": 185,
      "currentMonthEarned": 70,
      "todayClaimedCount": 2,
      "todayClaimLimit": 3
    },
    "success": {
      "totalSuccessDays": 9,
      "currentStreakDays": 9,
      "maxAchievedStreakDays": 9
    },
    "unlockProgress": {
      "nextItemMilestonePoints": 200,
      "nextStoryEpisodeNumber": 1,
      "nextStoryRequiredStreakDays": 10
    },
    "routines": [
      {
        "dailyRoutineId": 405,
        "routineId": 101,
        "content": "선크림 바르기",
        "serviceDate": "2026-08-17",
        "startTime": "07:00",
        "endTime": "09:00",
        "actualStartAt": "2026-08-17T07:00:00+09:00",
        "actualEndAtExclusive": "2026-08-17T09:01:00+09:00",
        "verificationObject": "COSMETIC_CONTAINER",
        "status": "COMPLETED",
        "verificationType": "PHOTO"
      }
    ]
  }
}
```


### dayStatus

```text
NO_ROUTINE
IN_PROGRESS
SUCCESS
FAILED
```

### 409

닉네임/Avatar/SpeechStyle 중 하나라도 설정되지 않았다면:

```json
{
  "code": "ONBOARDING_INCOMPLETE",
  "message": "초기 설정을 먼저 완료해 주세요.",
  "details": [],
  "traceId": "..."
}
```

---

# 11. Speech Style API

말투 기능은 `speech_style_system_SRS_v2.7.md`를 최우선 기준으로 한다.

---

## 11.1 Preset 목록

```http
GET /api/v1/speech-style/presets
```

### 200

```json
{
  "data": [
    {
      "code": "CALM",
      "name": "차분하게",
      "description": "짧고 차분한 말투"
    }
  ]
}
```

프리셋은 DB Table이 필수는 아니다. 서버 Config/상수로 관리 가능하다.

---

## 11.2 현재 활성 말투 조회

```http
GET /api/v1/speech-style
```

### 200

```json
{
  "data": {
    "sourceType": "KAKAO_CHAT",
    "settings": {
      "speechLevel": "BANMAL",
      "sentenceLength": "SHORT",
      "directness": "HIGH",
      "warmth": "MEDIUM",
      "playfulness": "LOW",
      "emotionalIntensity": "MEDIUM",
      "profanityEnabled": false
    },
    "profanityDetected": true,
    "validMessageCount": 237,
    "updatedAt": "2026-08-17T18:00:00+09:00"
  }
}
```

대표 예시와 내부 `styleJson` 전체는 사용자 화면에 노출하지 않는다.

### 404

```text
STYLE_NOT_FOUND
```

---

## 11.3 Preset 활성화/전환

```http
POST /api/v1/speech-style/preset
```

### Request

```json
{
  "presetCode": "CALM"
}
```

### 처리

```text
Preset profile 후보 준비
→ Dialogue 40개 생성
→ 서버 검수
→ 모두 성공

짧은 DB Transaction:
기존 Profile update/replace
기존 Example/Dialogue 제거
새 Dialogue 저장
```

실패 시 기존 말투를 유지한다.

### 200

```json
{
  "data": {
    "sourceType": "PRESET",
    "presetCode": "CALM",
    "settings": {
      "speechLevel": "BANMAL",
      "sentenceLength": "SHORT",
      "directness": "MEDIUM",
      "warmth": "MEDIUM",
      "playfulness": "LOW",
      "emotionalIntensity": "MEDIUM",
      "profanityEnabled": false
    },
    "dialogueCount": 40
  }
}
```

### 503

새 대사 생성 실패:

```text
DIALOGUE_GENERATION_FAILED
```

기존 Profile은 유지한다.

---

## 11.4 Kakao ZIP 업로드

```http
POST /api/v1/speech-style/kakao/jobs
Content-Type: multipart/form-data
```

Form:

```text
file = KakaoTalk_Chat.zip
```

### 201

```json
{
  "data": {
    "jobId": "5bfef1b4-2be3-4ea0-9c3f-e43547799d07",
    "status": "WAITING_PARTICIPANT_SELECTION",
    "participants": [
      {
        "id": "p1",
        "displayName": "이지섭"
      },
      {
        "id": "p2",
        "displayName": "성현"
      }
    ],
    "expiresAt": "2026-08-17T19:00:00+09:00"
  }
}
```

### 서버 검증

- ZIP
- 업로드 크기
- 압축 해제 후 전체 크기
- Zip Slip
- 암호화 ZIP 거부
- TXT 존재
- 지원 Kakao format
- 서버 로그에 원문 기록 금지

참여자 ID는 Job 내부 임시 ID이며 장기 DB Entity ID가 아니다.

---

## 11.5 본인 선택·분석 시작

```http
POST /api/v1/speech-style/kakao/jobs/{jobId}/analyze
```

### Request

```json
{
  "participantId": "p1"
}
```

### 202

```json
{
  "data": {
    "jobId": "5bfef1b4-2be3-4ea0-9c3f-e43547799d07",
    "status": "PREPROCESSING",
    "pollAfterMs": 2000
  }
}
```

### 처리

```text
본인 메시지 선택
→ 전처리
→ 개인정보 마스킹
→ 50개 검사
→ 최신 최대 500개
→ OpenAI 분석 1
→ OpenAI 분석 2
→ 최대 20 Example
→ Dialogue 40개
→ 검수
→ 성공 시 활성 Profile 원자적 교체
→ 원본/임시 데이터 삭제
```

---

## 11.6 분석 Job 조회

```http
GET /api/v1/speech-style/kakao/jobs/{jobId}
```

### Status Enum

```text
UPLOADED
WAITING_PARTICIPANT_SELECTION
PREPROCESSING
ANALYZING
GENERATING_DIALOGUES
COMPLETED
FAILED
EXPIRED
```

### 처리 중

```json
{
  "data": {
    "jobId": "...",
    "status": "ANALYZING",
    "pollAfterMs": 2000,
    "expiresAt": "2026-08-17T19:00:00+09:00"
  }
}
```

### Polling 정책

- `analyze` 202 응답 후 약 2초 뒤 첫 조회
- 처리 중에는 `pollAfterMs` 기본값 2000ms를 따른다.
- `COMPLETED`, `FAILED`, `EXPIRED`에서 즉시 중단한다.
- 브라우저 백그라운드 전환으로 간격이 길어지는 것은 허용한다.
- 1초 이하 polling을 기본값으로 사용하지 않는다.

### 완료

```json
{
  "data": {
    "jobId": "...",
    "status": "COMPLETED",
    "result": {
      "sourceType": "KAKAO_CHAT",
      "dialogueCount": 40,
      "validMessageCount": 237
    }
  }
}
```

### 410

Job 만료:

```text
ANALYSIS_EXPIRED
```

늦게 도착한 OpenAI 결과는 현재 Profile을 덮어쓸 수 없다.

---

## 11.7 말투 옵션 수정

```http
PATCH /api/v1/speech-style
```

### Request

모든 필드는 선택이다.

```json
{
  "speechLevel": "BANMAL",
  "sentenceLength": "MEDIUM",
  "directness": "MEDIUM",
  "warmth": "HIGH",
  "playfulness": "LOW",
  "profanityEnabled": false
}
```

### 검증

`profanityEnabled=true`는:

```text
profanityDetected = true
```

일 때만 가능하다.

### 처리

새 대사 40개 생성/검수가 먼저 성공해야 실제 Profile을 교체한다.

### 200

```json
{
  "data": {
    "settings": {
      "speechLevel": "BANMAL",
      "sentenceLength": "MEDIUM",
      "directness": "MEDIUM",
      "warmth": "HIGH",
      "playfulness": "LOW",
      "emotionalIntensity": "MEDIUM",
      "profanityEnabled": false
    },
    "dialogueCount": 40
  }
}
```

---

## 11.8 말투 초기화

```http
DELETE /api/v1/speech-style
```

### 204

삭제:

```text
SpeechStyleProfile
SpeechStyleExample
AvatarDialogue
말투 설정/metadata
```

유지:

```text
User
Avatar
Routine
DailyRoutine
RoutineVerification
DailySuccessRecord
UserItem
ItemUnlockRecord
UserStoryUnlock
```

이후 `/home`은 `409 ONBOARDING_INCOMPLETE`.

---

# 12. Routine API

## 12.1 인증 물건 지원 목록

```http
GET /api/v1/verification-objects
```

기존 계약을 유지한다.

## 12.2 카테고리별 추천 Routine

```http
GET /api/v1/routine-recommendations?category=SKIN
```

추천 데이터는 DB가 아니라 서버의 사전 검수 Config/JSON/YAML Pool을 기본으로 한다. 실시간 OpenAI 생성은 MVP에서 사용하지 않는다.

### 200

```json
{
  "data": [
    {
      "code": "SKIN_SUNSCREEN",
      "category": "SKIN",
      "content": "외출 전 선크림 바르기",
      "recommendedVerificationObject": "COSMETIC_CONTAINER"
    }
  ],
  "meta": { "count": 3 }
}
```

- 기본 최대 3개
- 이미 사용자가 등록한 동일 content 추천은 가능한 범위에서 제외
- 추천 선택은 폼을 채울 뿐 자동 저장하지 않음

## 12.3 Routine 목록/상세

```http
GET /api/v1/routines
GET /api/v1/routines/{routineId}
```

각 Routine에 `category`, `repeatType`, 필요 시 `scheduledDate`를 반환한다.

## 12.4 Routine 생성

```http
POST /api/v1/routines
```

### 반복 Routine Request

```json
{
  "category": "SKIN",
  "content": "선크림 바르기",
  "startTime": "07:00",
  "endTime": "09:00",
  "repeatType": "DAYS_OF_WEEK",
  "daysOfWeek": ["MON", "WED", "FRI"],
  "verificationObject": "COSMETIC_CONTAINER"
}
```

### TO_DO Request

```json
{
  "category": "TO_DO",
  "content": "영양제 챙기기",
  "scheduledDate": "2026-08-19",
  "startTime": "20:00",
  "endTime": "21:00",
  "repeatType": "ONCE",
  "daysOfWeek": [],
  "verificationObject": "SUPPLEMENT_CONTAINER"
}
```

### Category / Repeat validation

```text
SKIN / WELL_BEING / HEALTH_FIT / DIET
→ DAILY or DAYS_OF_WEEK

TO_DO
→ ONCE
→ scheduledDate 필수

DAILY
→ daysOfWeek 없음

DAYS_OF_WEEK
→ 최소 1개

ONCE
→ daysOfWeek 없음
```

공통으로 `startTime < endTime`, `24:00` 미지원, 지원 verificationObject를 검증한다.

반복 Routine의 당일 적용과 집합 고정 규칙은 기존 계약을 유지한다. `ONCE`는 scheduledDate에 DailyRoutine 1개만 materialize한다.

## 12.5 Routine 수정

```http
PATCH /api/v1/routines/{routineId}
```

카테고리/반복 타입 조합 validation을 동일하게 적용한다. 이미 시작된/집합 고정된 현재 날짜와 과거 DailyRoutine, Verification, PointClaim은 소급 변경하지 않는다.

## 12.6 Routine 삭제

```http
DELETE /api/v1/routines/{routineId}
```

soft delete를 사용하고 과거/고정 DailyRoutine, Verification, PointClaim, Success/Unlock 기록을 유지한다.

# 13. DailyRoutine / Mission API

## 13.1 날짜별 DailyRoutine 조회

```http
GET /api/v1/daily-routines?date=2026-08-17
```

`date` 생략 시 서버 기준 오늘.

호출 시 `ensureMaterialized`를 수행할 수 있다.

단, 단순 조회 날짜 한 건만 임의 생성하는 방식이 아니라 DB Design의 **gap backfill + horizon** 정책을 따른다.

### 200

```json
{
  "data": {
    "serviceDate": "2026-08-17",
    "dayStatus": "IN_PROGRESS",
    "completedCount": 1,
    "totalCount": 3,
    "percentage": 33,
    "routines": [
      {
        "id": 405,
        "routineId": 101,
        "category": "SKIN",
        "content": "선크림 바르기",
        "startTime": "07:00",
        "endTime": "09:00",
        "actualStartAt": "2026-08-17T07:00:00+09:00",
        "actualEndAtExclusive": "2026-08-17T09:01:00+09:00",
        "verificationObject": "COSMETIC_CONTAINER",
        "status": "COMPLETED",
        "verification": {
          "type": "PHOTO",
          "verifiedAt": "2026-08-17T07:42:12+09:00"
        },
        "pointClaim": {
          "claimed": false,
          "claimable": true,
          "rewardPoints": 10
        }
      },
      {
        "id": 406,
        "routineId": 102,
        "content": "물 마시기",
        "startTime": "18:00",
        "endTime": "20:00",
        "actualStartAt": "2026-08-17T18:00:00+09:00",
        "actualEndAtExclusive": "2026-08-17T20:01:00+09:00",
        "verificationObject": "CUP",
        "status": "AVAILABLE",
        "verification": null
      }
    ]
  }
}
```

---

## 13.2 Photo Mission 준비/조회

```http
POST /api/v1/daily-routines/{dailyRoutineId}/photo-mission
```

Mission 배정이 첫 호출에서 발생할 수 있으므로 GET이 아니라 POST를 사용한다.

### 정책

```text
이미 mission_template_id 있음
→ 기존 Mission 반환

없음
→ 사전 Mission pool에서 선택
→ 같은 사용자 직전 Mission 반복 가능하면 회피
→ DailyRoutine에 저장
→ 반환
```

이 Endpoint 자체는 Routine을 완료하지 않는다.

### 200

```json
{
  "data": {
    "dailyRoutineId": 406,
    "verificationObject": "CUP",
    "mission": {
      "templateId": 8,
      "gestureCode": "THUMBS_UP",
      "instruction": "컵과 함께 엄지척 해주세요."
    },
    "actualEndAtExclusive": "2026-08-17T20:01:00+09:00"
  }
}
```

### 시간창

시작 전에도 Mission 미리보기 허용 여부는 UI 선택이지만,
인증 가능 여부는 별개다.

v4.1에서는 Mission 조회는 시작 전에도 허용한다.

이미 종료된 Routine도 과거 Mission 조회는 가능하지만 새 인증은 불가능하다.

---

# 14. Verification API

PHOTO/CHECK 성공은 동일한 내부 `RoutineCompletionService`로 연결한다.

성공 Verification은 DailyRoutine당 최대 1건이다.

DB:

```text
UNIQUE(routine_verifications.daily_routine_id)
```

---

## 14.1 PHOTO 인증

```http
POST /api/v1/daily-routines/{dailyRoutineId}/verifications/photo
Content-Type: multipart/form-data
```

Form:

```text
photo = image/jpeg
```

클라이언트는 다음을 보내지 않는다.

```text
userId
serviceDate
missionCode
verificationRequestedAt
```

서버가 DailyRoutine에서 모두 조회하고 요청 수신 시각을 직접 캡처한다.

### 처리 순서

```text
1. auth
2. DailyRoutine ownership
3. verificationRequestedAt = server now
4. time window 1차 검사
5. 기존 Verification 확인
6. 임시 photo 확보
7. DailyRoutine mission 조회
8. AI object + gesture 판정
9. 성공 시 DB Completion Transaction
10. finally 사진 삭제
```

### 종료 경계

```text
verificationRequestedAt < actualEndAtExclusive
→ AI 처리 시작 가능

AI 완료 시각 >= actualEndAtExclusive
→ 최초 요청 시각이 유효했으므로 성공 처리 가능
```

종료 후 들어온 새 요청:

```text
422 ROUTINE_WINDOW_CLOSED
```

AI를 호출하지 않는다.

### 성공 200

```json
{
  "data": {
    "verification": {
      "id": 880,
      "dailyRoutineId": 406,
      "type": "PHOTO",
      "verifiedAt": "2026-08-17T18:55:11+09:00"
    },
    "dailyRoutine": {
      "status": "COMPLETED"
    },
    "dayResult": {
      "serviceDate": "2026-08-17",
      "dayStatus": "SUCCESS",
      "newlySucceeded": true,
      "completedCount": 3,
      "totalCount": 3
    },
    "successSummary": {
      "totalSuccessDays": 10,
      "currentStreakDays": 10,
      "maxAchievedStreakDays": 10
    },
    "pointClaim": {
      "autoAwarded": false,
      "claimable": true,
      "rewardPoints": 10
    },
    "unlocks": {
      "stories": [
        {
          "episodeNumber": 1,
          "requiredStreakDays": 10
        }
      ],
      "avatarStageChanged": {
        "changed": true,
        "previousStage": 1,
        "currentStage": 2
      }
    },
    "dialogue": {
      "situation": "ALL_COMPLETED",
      "content": "오늘 다 했네. 이 정도면 괜찮은데?"
    }
  }
}
```

### 인증 응답의 아바타 대사

프론트가 인증 성공 직후 대사를 받기 위해 별도 API를 다시 호출하지 않도록 완료 상황 대사를 인증 응답에 포함한다.

기본 선택:

```text
일부 루틴만 완료
→ ROUTINE_COMPLETED

마지막 루틴 완료로 하루 전체 성공
→ ALL_COMPLETED
```

사용자가 홈에서 아바타를 직접 눌러 새 대사를 요청하는 경우에는 `/avatar-dialogues/selections`를 사용한다.

개별 Routine만 완료되고 하루 성공이 아직 아니면:

```json
{
  "dayResult": {
    "dayStatus": "IN_PROGRESS",
    "newlySucceeded": false
  },
  "unlocks": {
    "stories": [],
    "avatarStageChanged": {
      "changed": false,
      "previousStage": 1,
      "currentStage": 1
    }
  },
  "dialogue": {
    "situation": "ROUTINE_COMPLETED",
    "content": "하나 끝. 다음 것도 시간 되면 하면 됨."
  }
}
```

### 사진 판정 실패 422

```json
{
  "code": "PHOTO_VERIFICATION_FAILED",
  "message": "사진에서 미션을 확인하지 못했습니다.",
  "details": [
    {
      "field": "photo",
      "reason": "요청한 물건 또는 손동작을 확인하지 못했습니다."
    }
  ],
  "traceId": "...",
  "data": {
    "canRetryPhoto": true,
    "canUseCheck": true
  }
}
```

실패 Verification row는 만들지 않는다.

### AI 장애 503

```json
{
  "code": "PHOTO_AI_UNAVAILABLE",
  "message": "사진 확인이 잠시 어렵습니다.",
  "details": [],
  "traceId": "...",
  "data": {
    "canUseCheck": true
  }
}
```

현재 시간이 종료된 경우 `canUseCheck=false`.

---

## 14.2 CHECK 인증

```http
POST /api/v1/daily-routines/{dailyRoutineId}/verifications/check
```

Body 없음.

Endpoint 호출 자체가 사용자의 수행 확인 의사다.

### 시간

PHOTO와 동일하게 서버 요청 수신 시각으로 검사한다.

### 200

성공 Response는 PHOTO와 동일한 Completion Result 구조를 사용하며:

```json
{
  "verification": {
    "type": "CHECK"
  }
}
```

만 다르다.

### 중복 409

```text
ALREADY_VERIFIED
```

PHOTO 성공 이후 CHECK 또는 CHECK 성공 이후 PHOTO 모두 동일하다.

---

# 15. Completion Transaction API Contract

PHOTO/CHECK Verification 성공과 Point 수령을 분리한다.

Verification 성공 Transaction:

```text
Verification INSERT
→ Day 전체 완료 검사
→ 필요 시 DailySuccessRecord INSERT
→ maxAchievedStreak 계산
→ Story Unlock
→ Avatar Stage
```

**Point와 Item은 여기서 지급하지 않는다.** 프론트는 완료 Routine에 `pointClaim.claimable/rewardPoints`를 표시할 수 있다.

Point Claim Transaction은 별도 Endpoint에서 수행한다.

```text
PointClaim INSERT
→ totalEarnedPoints
→ 도달한 100P Item milestone 처리
```


# 15.1 Point Claim API

```http
POST /api/v1/daily-routines/{dailyRoutineId}/point-claim
```

Body 없음. Point 값은 클라이언트가 보내지 않는다.

### 처리 조건

```text
DailyRoutine 소유권 확인
RoutineVerification 존재
server current serviceDate == DailyRoutine.serviceDate
해당 DailyRoutine PointClaim 없음
해당 serviceDate 기존 PointClaim count < 3
```

금액:

```text
Verification PHOTO → 10P
Verification CHECK → 5P
```

### 200

```json
{
  "data": {
    "dailyRoutineId": 406,
    "awardedPoints": 10,
    "todayClaimedCount": 3,
    "todayClaimLimit": 3,
    "totalEarnedPoints": 200,
    "itemUnlock": {
      "newlyUnlocked": true,
      "milestonePoints": 200,
      "item": {
        "id": 31,
        "name": "사자 선글라스",
        "type": "ACCESSORY",
        "assetKey": "items/accessory/31"
      }
    }
  }
}
```

Item이 없거나 milestone이 아니면 `itemUnlock`은 `null` 또는 `newlyUnlocked=false`. Point는 Item 해금으로 차감되지 않는다.

### 오류

- `ROUTINE_NOT_COMPLETED`
- `POINT_ALREADY_CLAIMED`
- `POINT_CLAIM_LIMIT_REACHED`
- `POINT_CLAIM_EXPIRED`

`POINT_CLAIM_EXPIRED`는 server current serviceDate가 target serviceDate와 다를 때 사용한다.

# 16. Item API

## 16.1 Item 목록/도감

```http
GET /api/v1/items?type=ACCESSORY&ownedOnly=false
```

Query:

```text
type      optional
ownedOnly optional, default false
```

### 200

```json
{
  "data": [
    {
      "id": 31,
      "name": "사자 선글라스",
      "type": "ACCESSORY",
      "assetKey": "items/accessory/31",
      "owned": true,
      "equipped": true,
      "acquiredAt": "2026-08-17T18:55:11+09:00"
    },
    {
      "id": 32,
      "name": "파란 모자",
      "type": "ACCESSORY",
      "assetKey": "items/accessory/32",
      "owned": false,
      "equipped": false,
      "acquiredAt": null
    }
  ]
}
```

Item price/currency 필드는 없다. Point로 Item을 구매하지 않는다. Item은 누적 획득 Point 100P milestone에서 자동 해금된다.

---

## 16.2 Equipment 변경

```http
PUT /api/v1/avatars/me/equipment
```

MVP는 Item 여러 개 동시 장착과 프론트 정적 PNG overlay로 확정한다. API는 **최종 장착 Item 전체 목록**을 받는 방식으로 유지한다.

### Request

```json
{
  "equippedItemIds": [31, 44]
}
```

### 검증

- 모두 본인이 보유한 Item
- inactive Item 처리 정책은 기본적으로 기존 보유는 허용하되 신규 장착은 거부
- backend는 slot/x/y/bodyPart/layerOrder를 받거나 저장하지 않음
- 모든 Item PNG는 프론트에서 Avatar와 같은 250×500 투명 캔버스로 관리
- 여러 Item ID 동시 장착 허용
- 동일 ID 중복 금지

현재 확정되지 않은 다음 규칙은 서버가 임의 생성하지 않는다.

```text
ACCESSORY 1개 제한
HAIR 1개 제한
slot별 최대 개수
layer order
```

프론트 2D 구현 방식 확정 후 최소 validation을 추가한다.

### 200

```json
{
  "data": {
    "equippedItems": [
      {
        "itemId": 31,
        "type": "ACCESSORY",
        "assetKey": "items/accessory/31"
      },
      {
        "itemId": 44,
        "type": "BACKGROUND",
        "assetKey": "items/background/44"
      }
    ]
  }
}
```

DB Source of Truth:

```text
UserItem.equipped
```

Avatar JSON에 item ID를 중복 저장하지 않는다.

---


# 16.5 Competition API

## 16.5.1 월간 Point Ranking

```http
GET /api/v1/competition/leaderboard?month=2026-08
```

`month` 생략 시 `Asia/Seoul` 기준 현재 월.

### 200

```json
{
  "data": {
    "month": "2026-08",
    "ranking": [
      { "rank": 1, "nickname": "멋사1", "earnedPoints": 250, "me": false },
      { "rank": 2, "nickname": "멋사2", "earnedPoints": 210, "me": true },
      { "rank": 2, "nickname": "멋사3", "earnedPoints": 210, "me": false },
      { "rank": 4, "nickname": "멋사4", "earnedPoints": 190, "me": false }
    ],
    "myRank": 2,
    "myEarnedPoints": 210
  }
}
```

- 해당 월 `RoutinePointClaim` 합계만 사용
- 동점 공동 순위, 다음 순위는 `1,2,2,4` 방식
- 월이 바뀌면 새 달 Claim만 집계
- 누적 Point와 Item Unlock은 유지
- Point 소비/차감/배팅/부스트/Battle Pass API 없음

# 17. Story API

MVP Story:

```text
EP.1 10일
EP.2 20일
EP.3 30일
EP.4 40일
EP.5 50일
```

Story는 영구 해금이다.

Avatar Stage:

```text
미해금 → 1
EP.1   → 2
EP.2+  → 3
```

EP.3~EP.5에서도 Stage 3.

Story 실제 제목·썸네일·이미지·본문은 **프론트 정적 asset**으로 관리한다.

백엔드는 콘텐츠를 반환하지 않고 사용자의 해금/진행 상태만 반환한다.

## 17.1 Story 진행도/목록

```http
GET /api/v1/stories
```

### 200

```json
{
  "data": {
    "currentStreakDays": 27,
    "maxAchievedStreakDays": 27,
    "avatarStage": 3,
    "episodes": [
      {
        "episodeNumber": 1,
        "requiredStreakDays": 10,
        "unlocked": true,
        "unlockedAt": "2026-07-20T23:10:00+09:00"
      },
      {
        "episodeNumber": 3,
        "requiredStreakDays": 30,
        "unlocked": false,
        "unlockedAt": null
      }
    ]
  }
}
```

### 프론트 콘텐츠 매핑

```text
episodeNumber = 1
→ Frontend EP.1 정적 asset

episodeNumber = 2
→ Frontend EP.2 정적 asset
...
```

백엔드는 다음을 반환하거나 저장하지 않는다.

```text
title
thumbnail
story body
scene
contentAssetKey
```

잠긴 Episode에서 제목·썸네일·요구 일수·현재 진행도를 실제 UI에 어디까지 보여줄지는 디자인 결정 사항이다.

Backend API는 UI 정책과 무관하게 `episodeNumber`, `requiredStreakDays`, `unlocked`, `unlockedAt`을 일관되게 제공한다.

별도 Story 상세 Endpoint는 MVP에서 만들지 않는다.

---

# 18. Record API

## 18.1 기간 기록 조회

```http
GET /api/v1/records?fromDate=2026-08-01&toDate=2026-08-17
```

MVP 기본 최대 범위:

```text
31일
```

장기간 조회가 필요하면 추후 pagination/월 단위 Endpoint를 추가한다.

### 200

```json
{
  "data": {
    "period": {
      "fromDate": "2026-08-01",
      "toDate": "2026-08-17"
    },
    "summary": {
      "scheduledRoutineCount": 40,
      "completedRoutineCount": 32,
      "completionRate": 80,
      "photoVerificationCount": 20,
      "checkVerificationCount": 12,
      "totalSuccessDays": 10,
      "currentStreakDays": 4,
      "maxAchievedStreakDays": 10
    },
    "days": [
      {
        "serviceDate": "2026-08-17",
        "dayStatus": "SUCCESS",
        "completedCount": 3,
        "totalCount": 3,
        "routines": [
          {
            "dailyRoutineId": 405,
            "routineId": 101,
            "content": "선크림 바르기",
            "status": "COMPLETED",
            "verificationType": "PHOTO"
          }
        ]
      },
      {
        "serviceDate": "2026-08-16",
        "dayStatus": "NO_ROUTINE",
        "completedCount": 0,
        "totalCount": 0,
        "routines": []
      }
    ]
  }
}
```

별도 통계 Table을 만들지 않는다.

```text
DailyRoutine
RoutineVerification
DailySuccessRecord
```

에서 계산한다.

---

# 19. Unlock Progress

Home/Verification 성공 응답에서 다음 정보를 사용한다.

## Item

Item 진행도는 누적 획득 Point 기준이다.

```text
nextItemMilestonePoints = (floor(totalEarnedPoints / 100) + 1) * 100
```


현재 Item milestone은 누적 획득 Point의 모든 100P 배수다.

```text
100, 200, 300, 400, ...
```

예:

```json
{
  "totalEarnedPoints": 230,
  "nextItemMilestonePoints": 300
}
```

Point는 Item 해금 시 차감하지 않는다.

## Competition

```text
월간 Point 합계 정렬
동점 공동 순위 1,2,2,4
월 경계 Asia/Seoul
과거 월 조회
```

## Story

```text
10, 20, 30, 40, 50
```

EP.5 이후:

```json
{
  "nextStoryEpisodeNumber": null,
  "nextStoryRequiredStreakDays": null
}
```

---

# 20. Routine 중복/시간 겹침 설계

제품 문서에는 같은 시간대 Routine 금지 규칙이 없다.

v4.1 API 기본안:

```text
시간 겹침 허용
```

이유:

- 서로 다른 Routine을 같은 시간대에 수행할 수 있음
- 하루 성공은 모든 DailyRoutine 완료가 기준이므로 중복 Routine이 보상 악용에 유리하지 않음
- DB에 시간 UNIQUE를 추가할 필요가 없음
- 이후 정책 변경 시 Service validation만 추가 가능

단, 프론트 UX에서 완전히 동일한 Routine 생성 시 경고를 줄 수 있다.

서버가 현재 강제로 거부하지 않는다.

---

# 21. ServiceDate 집합 고정 API 영향

다음 상황에서는 현재 날짜의 DailyRoutine 목록을 변경하지 않는다.

```text
그 serviceDate에 성공 Verification 1건 이상
```

이후:

```text
Routine 생성
Routine 수정
Routine 삭제
```

은 다음 적용 가능한 반복일부터 반영한다.

따라서 Routine CRUD Response에는 항상:

```json
{
  "effectiveFrom": "2026-08-19",
  "appliedToCurrentServiceDate": false
}
```

를 포함한다.

프론트는 필요하면:

> 변경 내용은 다음 루틴부터 반영됩니다.

를 표시할 수 있다.

---

# 22. 현재 streak 계산 API 원칙

DB에 `currentStreak`를 저장하지 않는다.

현재 streak는 예정 날짜와 성공 날짜를 이용해 계산한다.

```text
Routine 없는 날
→ 건너뜀

Routine 있는 날 모두 성공
→ streak +1

Routine 있는 날 종료 후 실패
→ streak 중단
```

오늘 Routine이 아직 수행 가능 시간 안이고 실패가 확정되지 않았다면, 이전까지 이어진 streak를 아직 중단시키지 않는다.

PHOTO 요청이 종료 전에 접수됐고 AI 판정 중이면 해당 결과가 확정되기 전까지 실패로 확정하지 않는다.

---

# 23. 외부 AI 장애 Fallback

## Kakao 분석 장애

```text
재시도 1회
→ 실패
→ 기존 SpeechStyle이 있으면 유지
→ 사용자에게 Preset/재업로드 안내
```

## Dialogue 생성 장애

```text
새 Profile 교체 금지
기존 Profile 유지
```

## PHOTO AI 장애

수행 시간 안:

```text
503 PHOTO_AI_UNAVAILABLE
canUseCheck = true
```

종료 후:

```text
canUseCheck = false
```

CHECK 자체는 외부 AI에 의존하지 않는다.

---

# 24. 사진/Kakao 삭제 시점

## PHOTO

```text
multipart 수신
→ AI
→ 결과 처리
→ finally delete
```

다음 모두 삭제:

```text
성공
판정 실패
AI 오류
DB 오류
timeout
```

영속 DB에 Photo URL/Path/Binary를 저장하지 않는다.

## Kakao

성공:

```text
Profile + Examples + Dialogues 저장
→ ZIP/TXT/parsing/context 삭제
```

50개 미만:

```text
즉시 삭제
```

실패/만료:

```text
SRS의 재시도/10분 정책 종료 후 삭제
```

---

# 25. 권한 규칙

아래는 모든 사용자 Resource에 적용한다.

```text
URL의 routineId가 타 사용자 소유
→ 404

URL의 dailyRoutineId가 타 사용자 소유
→ 404

jobId가 타 사용자 소유
→ 404
```

403으로 타 사용자 Resource의 존재 여부를 알려주지 않는다.

---

# 26. 중복 요청 / 동시성

전역 `Idempotency-Key` 저장 시스템은 MVP에 도입하지 않는다.

중복 방지는 도메인 DB 제약과 Lock을 우선한다.

```text
Verification
UNIQUE(daily_routine_id)

DailySuccess
UNIQUE(user_id, service_date)

UserItem
UNIQUE(user_id, item_id)

PointClaim
UNIQUE(daily_routine_id)

ItemUnlockRecord
UNIQUE(user_id, required_points)

UserStoryUnlock
UNIQUE(user_id, episode_id)
```

Completion Transaction lock 순서:

```text
User
→ 해당 serviceDate DailyRoutine 전체(id ASC)
→ 하위 Resource
```

같은 DailyRoutine에 PHOTO/CHECK가 동시에 오면 한 요청만 성공한다.

패배한 요청:

```text
409 ALREADY_VERIFIED
```

---

# 27. API가 직접 만들지 않는 상태

다음은 조회 응답에 있을 수 있지만 DB cached field가 아니다.

```text
DailyRoutine.status
currentStreakDays
totalSuccessDays
avatarStage
progress.percentage
dayStatus
```

각 Source of Truth에서 계산한다.

---

# 28. MVP에서 API에 넣지 않는 필드

```text
xp
experience
coin
price
currency
skinScore
healthScore
futureAppearancePrediction
diagnosis
verificationPhotoUrl
rawKakaoText
rawOpenAiResponse
demoMode
```

---

# 29. 제품 소스 확정/미확정과 API 대응

## 29.1 PHOTO/CHECK Point — 확정

```text
PHOTO 완료 Routine Claim → 10P
CHECK 완료 Routine Claim → 5P
serviceDate당 최대 3개
인증 성공 즉시 자동 지급 X
당일에만 Claim
```

Verification API는 Point를 생성하지 않고 `claimable/rewardPoints`만 안내할 수 있다. 실제 Point/Item 처리는 Point Claim API에서 수행한다.

## 29.2 Avatar 2D 생성·저장 — 확정

```text
성장 트랙 4종 고정
optional facePhoto
Stage 1 기준 Stage 2/3
최초 세 장 일괄 준비
250×500 RGBA PNG
host disk asset set
현재 Story Stage 이미지만 제공
원본 facePhoto 즉시 삭제
재생성 1회
초기 실패 DEFAULT fallback
```

API는 `Avatar.appearance` JSON을 사용하지 않는다. `growthTrack`, `stage`, `imageEndpoint`, `assetSource`, `regenerationRemaining`을 사용한다.

## 29.3 Item 장착 — 확정

Item 여러 개 동시 장착을 허용한다. Item binary는 backend에서 내려주지 않고 프론트 정적 250×500 transparent PNG를 `assetKey`로 매핑한다.

Backend API/DB에는 다음 값을 만들지 않는다.

```text
slot
x
y
bodyPart
layerOrder
```

## 29.4 Item Milestone — 확정

Item milestone은 누적 획득 Point `100, 200, 300, ...`에서 처리한다. `nextItemMilestonePoints`를 사용하고 Point는 차감하지 않는다.

## 29.5 아직 UI 수준에서 미확정

- 잠긴 Story 제목/썸네일/진행도 공개 범위
- Avatar 생성 로딩/실패 안내의 세부 디자인
- Item pool 소진 이후 장기 운영 안내

Story 실제 콘텐츠와 Item PNG는 프론트 정적 asset이므로 위 UI 결정이 현재 backend schema를 바꾸지 않는다.

# 30. 주요 Acceptance Test

## Session/Onboarding

### Avatar

```text
GrowthTrack 4종만 허용, TO_DO 거부
Avatar 생성 후 growthTrack 변경 거부
facePhoto 없음 → 기본 얼굴 Template 기반 생성
유효한 facePhoto → 얼굴 reference 사용, 원본 cleanup
부적절한 facePhoto → AVATAR_FACE_PHOTO_INVALID
Stage 1/2/3 모두 준비 후 활성 set 반영
최종 파일 250×500 PNG/RGBA
현재 Story Stage image만 GET /avatars/me/image로 반환
Stage 2/3 미해금 선노출 없음
초기 생성 실패 자동 재시도 → DEFAULT set fallback
재생성 성공 1회 → 기존 set 교체, regenerationRemaining=0
재생성 실패 → 기존 set 유지
Item 여러 개 장착 → equippedItems 복수 반환, 좌표 필드 없음
```

```text
Guest 생성
→ nickname 없음
→ NICKNAME_SETUP

nickname 설정
→ AVATAR_SETUP

Avatar 설정
→ SPEECH_STYLE_SETUP

Preset/Kakao 완료
→ HOME

Speech reset
→ SPEECH_STYLE_SETUP
→ /home 409
```

## Routine

```text
DAILY
MON/WED/FRI
같은 시간 2개 Routine
23:00~01:00 생성 거부
24:00 입력 거부
22:00~23:59 정상
과거 startTime Routine 신규 생성
첫 Verification 후 당일 수정
첫 Verification 후 당일 삭제
장기 미접속 gap backfill
```

## Status

```text
시작 전 UPCOMING
시간창 안 AVAILABLE
성공 COMPLETED
종료 후 미인증 FAILED
```

## Verification

```text
시작 1초 전 ROUTINE_NOT_STARTED
시작 정확히 성공 가능
종료 정확히 성공 가능
종료 직후 ROUTINE_WINDOW_CLOSED

PHOTO 실패 → 시간 남음 → retry/check
PHOTO 실패 → 종료 → retry/check 불가

PHOTO/CHECK 동시
PHOTO 후 CHECK
CHECK 후 PHOTO
```

## Daily Success

```text
3개 중 2개 완료 → IN_PROGRESS
3개 완료 → SUCCESS
1개 종료 실패 → FAILED
Routine 0개 → NO_ROUTINE
같은 date 중복 success 없음
```

## Item

```text
totalSuccess 0→1
2→3
4→5
9→10
19→20
29→30
39→40
49→50

미보유 Item random
Item 모두 소유 → unlock record itemId null
중복 milestone 없음
```

## Story

```text
streak 9→10 EP.1 Stage 2
19→20 EP.2 Stage 3
29→30 EP.3 Stage 3
39→40 EP.4 Stage 3
49→50 EP.5 Stage 3
failure 후 Story 잠금 해제 상태 유지
Stage 퇴화 없음
```

## Speech

```text
49 valid messages → fail
50 → analyze
501 → 최신 500

Kakao→Preset generation fail
→ 기존 Kakao 유지

Preset→Kakao analysis fail
→ 기존 Preset 유지

success
→ Profile + 40 Dialogue atomic replace

reset
→ Speech only delete
```

---

# 31. Frontend가 의존해도 되는 핵심 Enum

## OnboardingStep

```text
NICKNAME_SETUP
AVATAR_SETUP
SPEECH_STYLE_SETUP
HOME
```

## AvatarGrowthTrack

```text
SKIN
WELL_BEING
HEALTH_FIT
DIET
```

`TO_DO`는 AvatarGrowthTrack이 아니다.

## RoutineCategory

```text
SKIN
WELL_BEING
HEALTH_FIT
DIET
TO_DO
```

## RepeatType

```text
DAILY
DAYS_OF_WEEK
ONCE
```

## DayOfWeek

```text
MON
TUE
WED
THU
FRI
SAT
SUN
```

## DailyRoutineStatus

API-only derived:

```text
UPCOMING
AVAILABLE
COMPLETED
FAILED
```

## DayStatus

API-only derived:

```text
NO_ROUTINE
IN_PROGRESS
SUCCESS
FAILED
```

## VerificationType

```text
PHOTO
CHECK
```

## SpeechSourceType

```text
KAKAO_CHAT
PRESET
```

## SpeechAnalysisJobStatus

```text
UPLOADED
WAITING_PARTICIPANT_SELECTION
PREPROCESSING
ANALYZING
GENERATING_DIALOGUES
COMPLETED
FAILED
EXPIRED
```

## DialogueSituation

```text
ROUTINE_UPCOMING
ROUTINE_AVAILABLE
ROUTINE_REMINDER
ROUTINE_COMPLETED
ALL_COMPLETED
STREAK_CONTINUED
STREAK_BROKEN
RETURN_AFTER_ABSENCE
```

---

# 32. API 구현 순서

해커톤 일정에서는 Endpoint 번호보다 **핵심 사용자 흐름** 순서로 구현한다.

## Phase 1 — Session / Avatar 기본 흐름

```text
POST /sessions
GET/PATCH /users/me
PUT /avatars/me
GET /avatars/me
GET /avatars/me/image
```

Avatar 최초 생성은 기본 Stage fallback까지 먼저 연결해 온보딩이 외부 AI 장애로 막히지 않게 한다. `POST /avatars/me/regenerate`는 최초 생성 흐름이 안정된 뒤 같은 Phase 안에서 추가한다.

## Phase 2 — Routine / 추천

```text
GET /verification-objects
GET /routine-recommendations
GET/POST/PATCH/DELETE /routines
GET /daily-routines
```

`RoutineCategory`, `ONCE`, `TO_DO`를 함께 검증한다.

## Phase 3 — CHECK로 핵심 도메인 완주

```text
POST CHECK verification
→ DailySuccess
→ Story Unlock
→ Avatar Stage 변경

POST point-claim
→ RoutinePointClaim
→ 누적 Point
→ 100P Item Unlock
```

사진 AI 없이 먼저 `Routine → 인증 → 하루 성공/Story → Point Claim/Item`을 끝까지 검증한다.

## Phase 4 — Speech / Home

```text
Preset SpeechStyle
Avatar Dialogue
Home
```

## Phase 5 — Photo Mission / PHOTO 인증

```text
Photo Mission
PHOTO Verification
임시 사진 cleanup
```

## Phase 6 — Kakao Speech Analysis

```text
Kakao Speech Analysis Job
Speech update/reset
```

## Phase 7 — 조회·시연 안정화

```text
Records
Competition leaderboard
Items/equipment
Stories
Avatar regenerate
통합 테스트
배포 환경 파일 저장/volume 테스트
```

---

# 33. Codex CLI에 이 API 명세를 줄 때

먼저:

```text
AGENTS.md
PRD v2.1
Prompt v4.0
Speech SRS v2.7
DB Design v1.8
API Spec v4.3
```

을 읽게 한다.

그 후 바로 전체 Controller를 만들지 않는다.

먼저 다음을 보고하게 한다.

```text
1. 현재 Controller/DTO/Exception 패턴
2. API v4.3과 이미 일치하는 Endpoint
3. 충돌하는 Endpoint
4. 기존 URL을 유지하는 것이 나은 부분
5. DTO 계획
6. Service 호출 경계
7. Validation
8. Transaction 경계
9. Error mapping
10. 테스트 계획
```

현재 저장소에 이미 사용 중인 URL이 있고 동등한 의미를 안전하게 제공한다면,
**단순 naming 차이만으로 불필요하게 URL을 갈아엎지 않는다.**

---

# 34. 구현 전 반드시 확인할 API 설계 항목

현재 제품 정책상 아래 항목은 **확정값**이다. 구현 단계에서 다시 TBD로 되돌리지 않는다.

```text
1. 같은 시간대 Routine
   → 허용

2. Avatar
   → GrowthTrack 4종, optional facePhoto
   → Stage 1 기준 Stage 2/3
   → 최초 Stage 1/2/3 일괄 준비
   → 250×500 transparent PNG
   → host disk asset set
   → 현재 Stage만 image API로 제공

3. Item equipment
   → 여러 개 동시 장착
   → frontend 250×500 static PNG overlay
   → backend 좌표/slot/layerOrder 없음

4. Point
   → PHOTO 10 / CHECK 5
   → 완료 후 직접 Claim
   → 당일 최대 3개
   → 소비/차감 없음
   → 누적 100P마다 Item
   → 월간 합계로 Competition
```

구현 전 확인해야 하는 것은 제품 정책이 아니라 **현재 저장소와의 차이, Migration 영향, 실제 배포 환경의 file volume 경로**다.

---

# 35. 최종 API Freeze 원칙

```text
1. Guest only. 정식 로그인 API 없음.

2. nickname → Avatar → SpeechStyle → Home 순서.

3. Entity ID는 DB 전략을 따라 BIGINT 기반 number.
   SpeechAnalysisJob/GuestSession 내부 ID만 UUID.

4. Avatar growthTrack은 SKIN/WELL_BEING/HEALTH_FIT/DIET 중 1개이며 MVP 변경 불가.

5. Avatar facePhoto는 optional이며 얼굴 reference에만 사용하고 각 요청 종료 후 삭제.

6. Avatar Stage 1/2/3은 최초 설정에서 준비하며 최종 250×500 transparent PNG.

7. 현재 Avatar Stage는 Story에서 파생. EP.1 Stage2, EP.2+ Stage3.

8. GET /avatars/me/image는 현재 Stage만 제공하고 미래 Stage는 선노출하지 않음.

9. Avatar 초기 생성 실패는 자동 재시도 후 DEFAULT 세트 fallback. 재생성은 성공 1회이며 실패 시 기존 세트 유지.

10. Item 여러 개 동시 장착 가능. Item PNG는 frontend static overlay, backend 좌표/slot 없음.

11. DailyRoutine status는 API 파생값이며 DB column 아님.

12. PHOTO/CHECK는 시간창 안에서만 성공 가능하고 DailyRoutine당 하나만 존재.

13. 종료 후 새 인증 금지. PHOTO는 서버 최초 수신 시각이 유효하면 AI 완료가 늦어져도 처리 가능.

14. Day Success는 serviceDate당 최대 1번.

15. Verification 성공 Transaction은 DailySuccess/Story/Avatar Stage를 처리하고 Point/Item을 자동 지급하지 않음.

16. Point Claim은 PHOTO 10P / CHECK 5P, 해당 serviceDate 당일, 사용자당 하루 최대 3개.

17. Item Unlock은 Point Claim 후 누적 획득 Point 100/200/300/... milestone에서 처리하며 Point 차감 없음.

18. Competition은 Asia/Seoul 달력 월의 Point Claim 합계, 동점 공동 순위.

19. Story Unlock은 연속 하루 성공 10/20/30/40/50 → EP.1~EP.5, 영구 해금.

20. Routine 인증 PHOTO 원본 영속 저장 금지.

21. Avatar 원본 facePhoto 영속 저장 금지.

22. Kakao 원본 영속 저장 금지.

23. SpeechStyle 전환/수정은 새 생성 성공 후 원자적으로 교체.

24. XP/Coin/Shop/Point spend/Battle Pass 없음.

25. Demo Mode 없음.

26. Story 실제 콘텐츠와 Item PNG는 frontend 정적 asset. Backend는 상태/assetKey만 제공.

27. Speech Analysis Job 기본 polling은 2초.
```

---

# 36. 최종 구현 대상 Endpoint 요약

```text
GET    /health

POST   /sessions

GET    /users/me
PATCH  /users/me

GET    /avatars/me
PUT    /avatars/me
GET    /avatars/me/image
POST   /avatars/me/regenerate
PUT    /avatars/me/equipment
POST   /avatar-dialogues/selections

GET    /home

GET    /speech-style/presets
GET    /speech-style
POST   /speech-style/preset
POST   /speech-style/kakao/jobs
POST   /speech-style/kakao/jobs/{jobId}/analyze
GET    /speech-style/kakao/jobs/{jobId}
PATCH  /speech-style
DELETE /speech-style

GET    /verification-objects
GET    /routine-recommendations?category=

GET    /routines
GET    /routines/{routineId}
POST   /routines
PATCH  /routines/{routineId}
DELETE /routines/{routineId}

GET    /daily-routines
POST   /daily-routines/{dailyRoutineId}/photo-mission
POST   /daily-routines/{dailyRoutineId}/verifications/photo
POST   /daily-routines/{dailyRoutineId}/verifications/check
POST   /daily-routines/{dailyRoutineId}/point-claim

GET    /records
GET    /items
GET    /stories
GET    /competition/leaderboard?month=YYYY-MM
```

총 **35개 Endpoint**를 기본 계약으로 한다.

실제 저장소에 이미 동등한 의미의 안정적인 Endpoint가 있다면 단순 naming 차이만으로 불필요하게 갈아엎지 않는다. 다만 제품 정책과 데이터 Source of Truth는 본 v4.3을 기준으로 맞춘다.

