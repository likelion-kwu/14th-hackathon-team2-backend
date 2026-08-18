# 갓생사자 Part C 구현 계획서 v1.0

> 기준일: 2026-08-19  
> 최근 상태 반영: 2026-08-19, 로컬 commit `473a815` 기준  
> 대상: User / Routine CRUD / Catalog / Photo Mission / Item 조회 / Story 조회  
> 목적: 현재 skeleton을 유지하면서 Part C의 Business Logic을 작은 작업 단위로 구현하고, Part A/B와의 파일 충돌 및 정책 중복 구현을 방지한다.

---

## 1. 결론

Part C는 다음 6개 구현 단위로 나눈다.

```text
C-1 User
→ C-2 Verification Object / Routine Recommendation Catalog
→ C-3 Routine CRUD
→ C-4 Photo Mission
→ C-5 Item Read
→ C-6 Story Read
```

현재 Part A의 Guest Session/Auth, DailyRoutine materialization, 날짜별 DailyRoutine 조회가 구현되어 Part C가 실제 인증 기반으로 개발할 수 있다. C-1 User, C-2 Catalog 조회 구조, C-3 Routine CRUD는 Part C 작업 브랜치 `feature/c-routine-catalog`에서 구현을 완료했고, 나머지 C-4~C-6 Service는 아직 501 상태다. C-2 운영 catalog 데이터는 사전 검수 목록이 없어 빈 상태로 유지하며, 확정 목록 입력은 `TBD`다.

다만 C-4, C-6은 남아 있는 Part A 소유 로직과 연결되어야 최종 완료된다.

- C-3 Routine은 기존 `DailyRoutineMaterializationService`를 재사용하고, Part C coordinator가 serviceDate 집합 고정 판정과 미래 DailyRoutine 재동기화를 연결한다.
- C-4 Photo Mission은 Part A 소유 `DailyRoutine`의 소유권 조회·행 잠금·mission 할당 기능이 필요하다.
- C-6 Story Read는 Part A가 계산하는 현재/최대 streak와 Avatar Stage 파생 결과를 재사용한다.

Part C가 이 로직을 별도로 복제하지 않는다. 연동 인터페이스와 수정 파일을 먼저 팀에 공유하고, 합의된 경계 안에서 구현한다.

---

## 2. 기준 문서와 우선순위

현재 저장소의 다음 문서를 기준으로 분석했다.

1. `AGENTS.md`
2. `docs/갓생사자_백엔드_업무분배_지침_v1.0.md`
3. `docs/갓생사자_API_SPEC_v4.3.md`
4. `docs/갓생사자_backend_database_design_v1.8.md`
5. `docs/갓생사자_PRD_v2.1.md`
6. `docs/project_common_prompt_v4.0.md`
7. `docs/갓생사자_GitHub_브랜치_운영_가이드_v1.0.md`

적용 우선순위는 다음과 같다.

```text
제품 범위·행동       → PRD v2.1
HTTP 계약·오류       → API SPEC v4.3
DB·제약·트랜잭션     → DB Design v1.8
파트 소유권·개발 흐름 → 백엔드 업무분배 지침 v1.0
브랜치·통합 절차       → GitHub 브랜치 운영 가이드 v1.0
```

과거 버전 문서와 changelog는 현재 정책으로 사용하지 않는다. 문서에 없는 값은 임의로 확정하지 않고 `TBD`로 남긴다.

---

## 3. 현재 저장소 분석

### 3.1 기술 및 구조

- Java 17, Spring Boot 4.1.0, Spring Web MVC, Spring Data JPA
- PostgreSQL datasource 환경변수와 Flyway V1~V4 적용 설정 완료
- `common/api`, `common/error`, `common/auth`, `common/time` 기반 구조 존재
- `GuestSessionCurrentUserProvider`가 Bearer token hash를 검증하고 현재 user ID를 제공
- `DailyRoutineMaterializationService`가 Routine별/사용자별 60일 horizon 생성과 gap backfill 제공
- `DefaultDailyRoutineService`가 materialization 후 날짜별 상태·진행률·인증·Point claim 상태를 조회
- Entity는 FK 연관관계 대신 주로 `Long ...Id`를 사용한 단방향·저결합 skeleton
- Controller/DTO/Service interface는 API v4.3의 35개 endpoint 기준으로 생성됨
- Part C의 User/Routine/Catalog는 실제 Service로 교체했고 PhotoMission/Item/Story Service는 `NotImplemented*Service`의 HTTP 501 상태
- Routine/DailyRoutine 관련 Entity factory와 조회 Repository method 일부가 Part A 구현 과정에서 추가됨
- 새 Auth/Materialization/DailyRoutine 테스트가 추가됐지만 mock 기반 unit test이며 실제 PostgreSQL E2E 환경은 아직 확인되지 않음
- Part C 전체 작업 branch는 `feature/c-routine-catalog`이며 기준 commit은 `473a815`
- 사용자 지시에 따라 C-1 작업 중 원격 fetch/pull/push/API 호출은 수행하지 않음

### 3.2 Part C의 현재 파일 상태

| 영역 | 상태 | 현재 기반 | 남은 작업 |
|---|---|---|---|
| User | 로컬 구현 완료 | DefaultUserService, nickname 변경, 설정 존재 여부, onboarding 단계 파생 | 실제 PostgreSQL Bearer API smoke/E2E |
| Routine CRUD | 로컬 구현 완료 | DefaultRoutineService, validator, user lock, materialization/schedule coordinator | 실제 PostgreSQL Bearer API smoke/E2E |
| Catalog | 조회 구조 구현 완료 | classpath JSON loader, 조회/필터 Service, catalog validation | 사전 검수 운영 데이터 입력 TBD |
| Photo Mission | 미구현(501) | DailyRoutine snapshot, template Entity/Repository | 소유권 lock, 기존 mission 재사용, 동시 할당, active template 선택 |
| Item | 미구현(501) | Controller/DTO/Entity/Repository | catalog + 사용자 보유 상태 합성 query |
| Story | 미구현(501) | Controller/DTO/Entity/Repository | episode/unlock mapping 및 Part A progression 연동 |

### 3.3 확인한 계약 차이 및 누락

1. API v4.3의 Routine CRUD 응답 `effectiveFrom`, `appliedToCurrentServiceDate` 필드를 `RoutineResponse`에 반영했다.
2. `scheduledDate`는 DB 별도 컬럼이 아니라 `repeat_type=ONCE`일 때 `routines.effective_from`으로 저장해야 한다.
3. 지원 인증 물건과 카테고리별 추천의 실제 사전 검수 목록이 현재 코드/리소스에 없다. 문서의 `CUP`, `COSMETIC_CONTAINER`, `SUPPLEMENT_CONTAINER` 등은 예시일 뿐 전체 확정 목록이 아니다.
4. `photo_mission_templates`, `items`, `story_episodes` 테이블은 있으나 현재 V1~V4 migration에 운영 seed가 없다.
5. 기존 materialization의 누락 row 생성은 그대로 재사용하고, Part C `RoutineScheduleCoordinator`가 수정/삭제 후 미인증·미고정 미래 row 제거와 재생성을 담당한다.
6. Photo Mission 할당을 위해 Part A 소유 `DailyRoutine`에 mission을 설정하는 domain method와 소유권+행 잠금 조회가 필요하지만 현재 없다.
7. Story 응답의 `currentStreakDays`, `maxAchievedStreakDays`, `avatarStage`는 Part A 소유 계산 결과다. Part C가 `DailySuccessRecord`를 직접 재해석해 별도 알고리즘을 만들면 안 된다.
8. C-1 unit/controller 계약 테스트는 완료됐다. 실제 Bearer token + PostgreSQL API smoke test는 통합 환경에서 수행한다.
9. Part C는 `feature/c-routine-catalog` 한 브랜치에서 C-1~C-6을 개발하고, Part 전체 완료 후 최신 develop에 최종 통합한다.

---

## 4. Part C 범위와 금지 범위

### 4.1 구현 범위

- `GET/PATCH /api/v1/users/me`
- `GET/POST/PATCH/DELETE /api/v1/routines...`
- `GET /api/v1/routine-recommendations`
- `GET /api/v1/verification-objects`
- `POST /api/v1/daily-routines/{dailyRoutineId}/photo-mission`
- `GET /api/v1/items`
- `GET /api/v1/stories`

### 4.2 구현하지 않는 것

- Session/token/auth filter 구현
- DailyRoutine materialization 알고리즘 자체
- PHOTO/CHECK Verification orchestration 및 AI 판정
- Point Claim, Item Unlock, Story Unlock/Streak 생성 로직
- Avatar Item 장착
- 실시간 AI Routine 추천
- 새로운 DB 상태 컬럼, Point wallet/balance, XP/Coin
- V1~V4 migration 수정

---

## 5. 공통 개발 원칙 적용

모든 작업 단위에서 다음을 공통으로 지킨다.

1. Controller는 요청 전달과 응답 wrapping만 담당하고 정책은 Service/domain에 둔다.
2. 모든 사용자 리소스는 `CurrentUserProvider`의 user ID로 조회하며 request/path의 client user ID를 신뢰하지 않는다.
3. 타 사용자 소유 Routine/DailyRoutine은 존재 여부를 숨기기 위해 `404`로 처리한다.
4. `RoutineVerification`, `DailySuccessRecord`, `RoutinePointClaim`, `UserItem`, `UserStoryUnlock` 등 문서의 Source of Truth를 재정의하지 않는다.
5. 기존 Entity field와 V1~V4 schema는 변경하지 않는다. 필요한 것은 생성자/domain method/repository query로 해결한다.
6. 루틴 수정·삭제로 과거 snapshot, Verification, PointClaim, Success, Unlock을 변경하지 않는다.
7. 다른 파트 소유 파일 수정이 필요하면 먼저 파일과 이유를 공유하고 합의 후 반영한다.
8. 작업 단위마다 관련 테스트 후 전체 `test`, `build`, diff·secret·민감 파일 점검을 수행한다.

---

## 6. 구현 단위별 계획

## C-0. 파트 간 계약 및 데이터 확정

현재 기반 구현과 남은 합의 항목을 다음처럼 구분한다.

### 합의 항목

1. 완료된 Part A 기반
   - Bearer token 기반 `CurrentUserProvider`
   - `ensureMaterializedForUser(userId)`
   - `ensureMaterializedForRoutine(routineId, throughDate)`
   - 날짜별 DailyRoutine 조회와 상태 파생
2. 추가 합의가 필요한 DailyRoutine 변경 계약
   - 현재 serviceDate 집합 고정 여부 조회
   - Routine 수정/삭제 전 현재일까지 역사 보충
   - 효력일 이후 미인증·미고정 DailyRoutine 제거/재생성
   - 응답용 `effectiveFrom`, `appliedToCurrentServiceDate`
3. Photo Mission용 DailyRoutine 소유권·행 잠금·mission 할당 방식
4. Story 조회에서 재사용할 Part A progression read model
   - `currentStreakDays`
   - `maxAchievedStreakDays`
   - `avatarStage`
5. 사전 검수 데이터
   - 지원 인증 물건 목록: `TBD`
   - 카테고리별 추천 Routine pool: `TBD`
   - 실제 테스트가 끝난 Photo Mission template/gesture: `TBD`
   - 실제 asset이 존재하는 Item seed: `TBD`

### 원칙

새 migration이 필요하면 V1~V4를 수정하지 않고 별도 migration을 제안한다. `docs/**`, Flyway, `common/**`, Part A 소유 파일은 팀 공유 없이 수정하지 않는다.

---

## C-1. User

### 구현 상태

```text
로컬 구현 완료
전체 test/build 통과
실제 PostgreSQL Bearer API smoke/E2E 대기
```

### 대상 API

```http
GET   /api/v1/users/me
PATCH /api/v1/users/me
```

### 구현 내용

- 인증 user ID로 `User` 조회
- Avatar 존재 여부와 SpeechStyleProfile 존재 여부 조회
- 다음 우선순위로 `nextStep` 파생

```text
nickname 없음          → NICKNAME_SETUP
nickname 있음/avatar 없음 → AVATAR_SETUP
avatar 있음/style 없음 → SPEECH_STYLE_SETUP
모두 완료             → HOME
```

- PATCH nickname은 trim 후 1~30자를 검증하고 trim된 값을 저장
- `createdAt`, `updatedAt`은 Asia/Seoul offset 응답으로 변환

### 예상 수정/생성 파일

- 수정: `user/domain/User.java` (`createGuest`는 유지하고 nickname 변경 method만 추가)
- 수정: `user/repository/UserRepository.java` 또는 기본 `findById` 유지
- 수정: `avatar/repository/AvatarRepository.java` (`existsByUserId` 추가, Part B에 공유)
- 수정: `speech/repository/SpeechStyleProfileRepository.java` (`existsByUserId` 추가, Part B에 공유)
- 생성: `user/application/DefaultUserService.java`
- 제거 또는 비활성화: `user/application/NotImplementedUserService.java`
- 생성 완료: `src/test/java/.../user/application/UserServiceTests.java`
- 생성 완료: `src/test/java/.../user/api/UserControllerTests.java`

### 트랜잭션

- GET: read-only transaction
- PATCH: User 한 행의 nickname/updatedAt 변경 transaction

Service는 실제 `CurrentUserProvider` interface에 연결했다. 현재 Controller 테스트는 HTTP 계약과 Jakarta validation을 검증하며, Bearer token부터 실제 DB까지의 smoke test는 PostgreSQL 통합 환경에서 수행한다.

### 중요 테스트

- 인증 user 기준 조회
- 각 onboarding 단계 4종
- 앞뒤 공백 trim
- trim 후 빈 문자열/31자 거부
- request에 userId가 없는 계약 유지

---

## C-2. Verification Object / Routine Recommendation Catalog

### 구현 상태 (2026-08-19)

- classpath JSON catalog 로딩과 시작 시 형식 검증 구현 완료
- 인증 물체 조회와 카테고리별 추천 조회 Service 연결 완료
- 인증 사용자와 category 기준 활성 Routine의 동일 content 제외 구현 완료
- 연속 공백을 하나로 정규화한 동일 content 비교와 최대 3개 제한 구현 완료
- 추천 조회가 Routine을 생성·수정하지 않는 조회 전용 흐름 확인
- query category 누락/잘못된 값은 `400 INVALID_ROUTINE_CATEGORY`로 처리
- C-3에서 재사용할 `supportsVerificationObject` 계약 구현 완료
- 사전 검수된 실제 code/name/content 목록 부재로 운영 JSON은 빈 배열 유지 (`TBD`)

### 대상 API

```http
GET /api/v1/verification-objects
GET /api/v1/routine-recommendations?category=
```

### 구현 내용

- 사전 검수 catalog를 코드 또는 classpath JSON/YAML로 관리
- 새로운 DB table과 실시간 OpenAI 호출은 추가하지 않음
- 추천은 선택한 category에 대해서만 기본 최대 3개 반환
- 현재 사용자의 활성 Routine content와 정확히 같은 추천은 가능한 범위에서 제외
- 추천 조회는 Routine을 생성·수정하지 않음
- Routine 생성/수정 시 같은 catalog를 사용해 `verificationObject` 지원 여부 검증

### 데이터 파일 제안

리뷰 편의와 무의존성을 위해 Jackson으로 읽는 단일 JSON 리소스를 우선 제안한다.

```text
src/main/resources/catalog/routine-catalog.json
```

실제 code/name/content는 사전 검수 목록을 받은 뒤 입력한다. 예시 값을 운영값으로 간주하지 않는다.

### 실제 수정/생성 파일

- 생성: `routine/catalog/RoutineCatalog.java`
- 생성: `routine/catalog/ClasspathRoutineCatalog.java`
- 생성: `src/main/resources/catalog/routine-catalog.json`
- 생성: `routine/application/DefaultRoutineCatalogService.java`
- 수정: `routine/repository/RoutineRepository.java` (현재 사용자 활성 content 조회)
- 제거 또는 비활성화: `NotImplementedRoutineCatalogService.java`
- 생성: `ClasspathRoutineCatalogTests`, `RoutineCatalogServiceTests`, `RoutineCatalogControllerTests`

### 실패 처리

- query category 누락/잘못된 enum: `400 INVALID_ROUTINE_CATEGORY`
- catalog에 없는 verification object로 Routine 저장: `VERIFICATION_OBJECT_NOT_SUPPORTED`
- catalog 리소스 형식 오류: 애플리케이션 시작 시 명확히 실패시키고 secret/raw data는 로그하지 않음

---

## C-3. Routine CRUD

### 대상 API

```http
GET    /api/v1/routines
GET    /api/v1/routines/{routineId}
POST   /api/v1/routines
PATCH  /api/v1/routines/{routineId}
DELETE /api/v1/routines/{routineId}
```

### 구현 상태 (2026-08-19)

- 현재 사용자 기준 활성 Routine 목록/상세와 소유권 404 구현 완료
- 공통 validation, content trim, category/repeat/day/date/time/object 검증 완료
- 반복 최초 적용일 계산과 `effectiveFrom`, `appliedToCurrentServiceDate` 응답 완료
- Routine 생성, DAYS_OF_WEEK row 저장, materialization 연동 완료
- user row lock을 사용한 쓰기 직렬화와 한 transaction 경계 적용
- 수정 전 기존 규칙의 현재일까지 역사 보충 완료
- 수정 시 미인증·미고정 DailyRoutine 제거 후 새 규칙 materialization 완료
- 삭제 soft delete와 과거/인증/고정 snapshot 보존 완료
- 과거/오늘 시작 시각 이후 ONCE 생성·수정은 `INVALID_ONCE_DATE`로 처리
- 오늘 집합이 잠긴 ONCE 생성·수정은 `SERVICE_DATE_LOCKED`로 처리
- C-3 단위/API 계약 테스트 완료
- 실제 PostgreSQL/Bearer API E2E는 통합 환경에서 확인 필요

### 3-1. 공통 validation

```text
SKIN/WELL_BEING/HEALTH_FIT/DIET → DAILY 또는 DAYS_OF_WEEK
TO_DO                            → ONCE
DAILY/ONCE                       → daysOfWeek 없음
DAYS_OF_WEEK                     → 중복 없는 요일 최소 1개
TO_DO/ONCE                       → scheduledDate 필수
반복 Routine                     → scheduledDate 없음
공통                              → trim content 1~100자
공통                              → startTime < endTime
공통                              → 지원 verificationObject만 허용
```

`24:00`은 `LocalTime` JSON parsing 단계에서 거부하고, 자정 넘김/동일 시간은 `INVALID_TIME_RANGE`로 처리한다.

과거 `scheduledDate`와 오늘이지만 이미 startTime이 지난 ONCE 생성/수정은 `INVALID_ONCE_DATE`로 거부한다. 오늘 집합이 잠긴 ONCE는 다음 반복일이 없으므로 `SERVICE_DATE_LOCKED`로 거부한다.

### 3-2. 소유권 및 조회

- 목록은 현재 사용자의 `deleted_at IS NULL` Routine만 조회
- 상세/수정/삭제는 `id + currentUserId + deleted_at IS NULL` 조건으로 조회
- 타 사용자 또는 삭제된 Routine은 `ROUTINE_NOT_FOUND` 404
- `DAYS_OF_WEEK`만 repeat day를 반환
- `ONCE` 응답의 `scheduledDate = effectiveFrom`, 나머지는 `null`

### 3-3. 생성

```text
CurrentUser
→ request 정규화/검증
→ 오늘 집합 고정 및 startTime 확인
→ effectiveFrom 계산
→ Routine 저장
→ DAYS_OF_WEEK이면 RoutineRepeatDay 저장
→ 기존 `DailyRoutineMaterializationService.ensureMaterializedForRoutine` 호출
→ RoutineResponse 반환
```

### 3-4. 수정

```text
CurrentUser 소유 Routine 조회
→ 변경 전 규칙의 현재일까지 materialization 보충(Part A)
→ request 검증
→ 당일 lock/startTime에 따른 effectiveFrom 계산
→ Routine 원본 변경
→ repeat days 교체
→ 효력일 이후 미인증·미고정 DailyRoutine만 재동기화(Part A)
→ 과거 snapshot 유지
```

### 3-5. 삭제

```text
CurrentUser 소유 Routine 조회
→ 변경 전 규칙의 현재일까지 materialization 보충(Part A)
→ deletedAt 기록
→ 효력일 이후 미인증 미래 DailyRoutine만 제거(Part A)
→ 과거/인증/고정 DailyRoutine 유지
→ 204
```

### 트랜잭션 경계

Routine 원본, repeat days, materialized DailyRoutine 변경은 한 transaction에서 성공하거나 모두 rollback되어야 한다. 생성은 현재 Part A Service를 사용하고, 수정/삭제는 Part A와 합의한 재동기화 operation을 사용한다. 외부 API는 호출하지 않는다.

### 실제 수정/생성 파일

- 수정: `routine/domain/Routine.java` (기존 factory 유지, update/softDelete 추가)
- 유지: `routine/domain/RoutineRepeatDay.java`, `RoutineRepeatDayId.java`의 기존 factory
- 수정: `routine/repository/RoutineRepository.java` (기존 materialization query 유지, 소유권 query 추가)
- 수정: `routine/repository/RoutineRepeatDayRepository.java` (기존 조회 유지, 교체용 delete/query 추가)
- 수정: `routine/dto/RoutineResponse.java` (`effectiveFrom`, `appliedToCurrentServiceDate` 계약 반영)
- 생성: `routine/application/RoutinePolicyValidator.java`
- 생성: `routine/application/DefaultRoutineService.java`
- 생성: `routine/application/RoutineScheduleCoordinator.java`
- 생성: `routine/application/DefaultRoutineScheduleCoordinator.java`
- 제거 또는 비활성화: `NotImplementedRoutineService.java`
- 사용: 기존 `routine/daily/application/DailyRoutineMaterializationService.java`
- 수정: `routine/daily/repository/DailyRoutineRepository.java` (재동기화 범위 조회만 추가)
- 수정: `user/repository/UserRepository.java` (쓰기 직렬화용 user row lock)
- 생성: `RoutinePolicyValidatorTests`, `RoutineServiceTests`, `RoutineScheduleCoordinatorTests`, `RoutineControllerTests`

### 중요 테스트

- 5개 category와 3개 repeat type 조합
- DAILY, DAYS_OF_WEEK, ONCE 생성
- 빈/중복 요일, 잘못된 scheduledDate, 미지원 verification object
- 동일/역전/자정 넘김 시간 거부, `22:00~23:59` 허용
- 타 사용자 Routine 404
- soft delete 및 삭제 목록 제외
- 첫 Verification 전 미래 시간대 당일 반영
- 시작 시간이 지난 Routine 수정/삭제의 당일 비소급
- 첫 Verification 후 당일 생성/수정/삭제 비반영
- 수정 후 과거 snapshot/Verification/PointClaim 유지
- ONCE DailyRoutine 정확히 1개

---

## C-4. Photo Mission

### 대상 API

```http
POST /api/v1/daily-routines/{dailyRoutineId}/photo-mission
```

### 구현 내용

```text
CurrentUser 소유 DailyRoutine을 lock 조회
→ mission_template_id가 있으면 기존 template 반환
→ 없으면 active template 목록 조회
→ 같은 사용자의 직전 mission과 다른 template을 우선 선택
→ 선택 결과를 DailyRoutine에 저장
→ verification object + instruction 조합 응답
```

- Endpoint는 완료/Verification/Point를 만들지 않음
- 시작 전 mission 조회 허용
- 종료된 Routine도 기존 mission 조회 허용
- 동시에 두 요청이 들어와도 최종 mission은 하나만 할당
- template 선택은 테스트 가능한 selector로 분리하고 cryptographic random은 요구하지 않음
- 현재 `DailyRoutine`에는 `createSnapshot`만 있으므로 mission 할당 domain method는 Part A에 먼저 공유

### 예상 수정/생성 파일

- 수정: `routine/repository/PhotoMissionTemplateRepository.java`
- 생성: `routine/application/PhotoMissionSelector.java`
- 생성: `routine/application/DefaultPhotoMissionService.java`
- 제거 또는 비활성화: `NotImplementedPhotoMissionService.java`
- Part A 합의 후 수정: `routine/daily/domain/DailyRoutine.java`의 mission 할당 method
- Part A 합의 후 수정: 기존 `routine/daily/repository/DailyRoutineRepository.java`에 소유권+잠금 query와 직전 mission query 추가
- 생성: service/concurrency/controller tests

### 실패 및 결정 필요 항목

- 타 사용자/없는 DailyRoutine: `DAILY_ROUTINE_NOT_FOUND` 404
- 활성 template이 0개인 운영 상태의 API error code는 현재 명세에 없음: `TBD`
- 실제 허용 gesture/template은 Part B의 AI 판정 테스트 완료 목록만 사용

---

## C-5. Item Read

### 대상 API

```http
GET /api/v1/items?type=&ownedOnly=false
```

### 구현 내용

- 현재 사용자의 UserItem을 조회해 Item catalog와 합성
- `owned`, `equipped`, `acquiredAt` 파생
- `ownedOnly=true`면 보유 Item만 반환
- `type`이 있으면 DB의 `item_type` 문자열과 일치하는 Item만 반환
- Item type은 현재 Freeze된 enum이 아니므로 새 enum/slot/layerOrder를 만들지 않음
- price/currency/Point 소비 필드를 만들지 않음
- 비활성 Item 노출 여부는 catalog 정책상 기본적으로 제외하는 안을 적용하되, 기존 보유 비활성 Item의 표시 필요 여부는 구현 전 확인

### 예상 수정/생성 파일

- 수정: `item/repository/ItemRepository.java`
- 수정: `item/repository/UserItemRepository.java`
- 생성: `item/application/DefaultItemService.java`
- 제거 또는 비활성화: `NotImplementedItemService.java`
- 생성: item service/controller/repository tests

### 트랜잭션

read-only transaction. Item Unlock이나 장착 상태를 변경하지 않는다.

---

## C-6. Story Read

### 대상 API

```http
GET /api/v1/stories
```

### 구현 내용

```text
CurrentUser
→ active StoryEpisode를 episodeNumber 순으로 조회
→ 현재 사용자의 UserStoryUnlock을 episodeId로 매핑
→ Part A ProgressSnapshot 조회
→ current/max streak + avatarStage + episodes 응답
```

- Story content/title/thumbnail/body/asset key는 반환하거나 저장하지 않음
- Unlock을 새로 만들거나 streak를 재계산하는 쓰기 로직은 구현하지 않음
- `avatarStage`는 저장하지 않고 Part A의 Story progression 결과에서 받음

### 예상 수정/생성 파일

- 수정: `story/repository/StoryEpisodeRepository.java`
- 수정: `story/repository/UserStoryUnlockRepository.java`
- 생성: `story/application/DefaultStoryService.java`
- 제거 또는 비활성화: `NotImplementedStoryService.java`
- Part A 합의 후 생성/의존: 아직 존재하지 않는 progression read interface
- 생성: story service/controller tests

### 트랜잭션

read-only transaction. Story Unlock 생성은 Part A만 수행한다.

### 중요 테스트

- episodeNumber 정렬
- 잠금/해금 상태와 unlockedAt mapping
- 해금 없음 → Stage 1
- EP.1 → Stage 2, EP.2 이상 → Stage 3
- 이미 해금한 Episode는 현재 streak가 낮아져도 unlocked 유지
- 프론트 콘텐츠 필드가 응답에 추가되지 않음

---

## 7. 파트 간 데이터 흐름 및 소유권

| 흐름 | Part C 책임 | Part A/B 책임 |
|---|---|---|
| User 조회 | User + 설정 존재 여부 mapping | A: 인증 user 제공 완료, B: Avatar/Speech 데이터 생성 |
| Routine CRUD | 입력 검증, Routine/RepeatDay 원본 변경 | A: 기본 materialization 완료, 변경 재동기화/lock 판정 남음 |
| Recommendation | 사전 catalog 조회, 등록 content 제외 | 없음, 실시간 AI 금지 |
| Photo Mission | template 선택·응답 | A: DailyRoutine 저장 경계, B: PHOTO AI 판정 |
| Item Read | catalog + ownership 상태 합성 | A: Item Unlock, B: 장착 변경 |
| Story Read | Episode + Unlock 응답 mapping | A: streak 계산/Unlock/Stage 파생 |

Part C에서 다른 파트 Service를 호출할 때는 파생 결과나 명령을 위한 작은 interface만 사용하고, 다른 파트의 내부 Repository 쿼리를 복제하지 않는다. 현재 구현된 `CurrentUserProvider`와 `DailyRoutineMaterializationService`를 우선 재사용한다.

---

## 8. 테스트 전략

### 8.1 계층별 테스트

1. Domain/validator unit test
   - category/repeat/date/time/day 조합
   - nickname 정규화
2. Service unit test
   - CurrentUser 기반 소유권
   - repository interaction
   - 응답 mapping
   - transaction 전제
3. MVC slice test
   - URL/method/status/body/error JSON
   - request validation
4. PostgreSQL integration test
   - Flyway V1~V4 적용
   - 실제 query/unique/soft delete/lock 동작
   - Routine + RepeatDay + DailyRoutine 원자성
   - Photo Mission 동시 요청
5. Part A/B 통합 test
   - Guest token → User → Routine CRUD
   - Routine → DailyRoutine → Mission → PHOTO/CHECK
   - Item/Story 조회 반영

### 8.2 DB 테스트 환경

현재 저장소에는 H2/Testcontainers가 없다. PostgreSQL 전용 제약과 Flyway를 검증해야 하므로 H2를 임의 추가하지 않는다. 팀의 실제 test PostgreSQL 방식 또는 Testcontainers 도입 여부를 먼저 확인한다. 테스트 의존성 추가가 합의되지 않으면 unit/MVC test와 별도로 실제 PostgreSQL 검증 절차를 명시한다.

### 8.3 매 작업 단위 검증 명령

```powershell
.\gradlew.bat test
.\gradlew.bat build
git diff --check
git status --short
```

추가 확인:

- `.env`, DB 비밀번호, API key가 추적되지 않는지
- 사진/Kakao 원문/생성 임시 파일이 생기지 않았는지
- V1~V4 migration과 다른 파트 핵심 Service가 변경되지 않았는지
- skeleton의 501 기대 테스트를 실제 기능 테스트로 교체했는지

### 8.4 현재 기준선

2026-08-19 로컬 `feature/c-routine-catalog`에서 C-1~C-3 구현을 포함한 상태로 다음을 확인했다.

```text
.\gradlew.bat test  → BUILD SUCCESSFUL
.\gradlew.bat build → BUILD SUCCESSFUL
테스트 결과          → 103 passed, 0 failed/skipped
```

현재 테스트에는 기존 `SessionAuthTests`, `DailyRoutineMaterializationServiceTests`, `DailyRoutineServiceTests`, C-1의 `UserServiceTests`, `UserControllerTests`, C-2의 catalog 테스트와 C-3의 `RoutinePolicyValidatorTests`, `RoutineServiceTests`, `RoutineScheduleCoordinatorTests`, `RoutineControllerTests`가 포함된다. 다만 mock/standalone 기반 테스트이므로 실제 PostgreSQL/Flyway/API E2E 검증을 대체하지 않는다.

---

## 9. 구현 순서와 일정 영향

### 1차: 독립 구현 가능

```text
feature/c-routine-catalog에서 C-1 User 로컬 구현 완료
같은 branch에서 C-2 Catalog 로직/테스트 완료, 운영 데이터 입력 TBD
같은 branch에서 C-3 Routine CRUD 로직/테스트 완료
```

Part A 인증에 의존하는 C-1 Service 연결, C-2 조회 구조, C-3 Routine CRUD와 단위/API 계약 테스트를 완료했다. 실제 Bearer token E2E는 PostgreSQL 통합 환경에서 남아 있으며, C-2의 실제 데이터 입력은 사전 검수 목록 확정 이후 완료된다.

### 2차: 핵심 사용자 흐름

```text
C-3 Routine CRUD 완료
→ 기존 Part A materialization Service 재사용 완료
→ 수정/삭제 재동기화 coordinator 추가 완료
→ Checkpoint 1 E2E 지원
→ 같은 feature/c-routine-catalog에서 C-4 구현
```

8월 21일 Must 흐름에 가장 직접적인 경로이므로 C-5/C-6보다 우선한다.

### 3차: 조회 통합

```text
C-5 Item Read
C-6 Story Read
→ Part A progression 연동
→ 전체 Part C 회귀 테스트
```

### 예상 소요

| 단위 | 예상 | 선행 조건 |
|---|---:|---|
| C-0 남은 계약/데이터 확인 | 0.25~0.5일 | 변경 재동기화, mission, progression, seed 확인 |
| C-1 User | 0.25~0.5일 | CurrentUserProvider 완료 |
| C-2 Catalog | 0.25~0.5일 | 사전 검수 데이터 |
| C-3 Routine CRUD | 1~1.5일 | 기본 materialization 완료, update/delete 계약 필요 |
| C-4 Photo Mission | 0.5일 | DailyRoutine lock/assignment, mission seed |
| C-5 Item Read | 0.25일 | Item seed/asset 목록 |
| C-6 Story Read | 0.25~0.5일 | Part A progression read model, Story seed |
| 통합/회귀 | 0.5일 이상 | 실제 PostgreSQL 및 Part A/B 구현 |

전체를 순차 구현하면 8월 21일 안정화 일정이 빠듯하다. C-1은 완료됐으므로 **C-2 Catalog → C-3 Routine CRUD → Checkpoint 1 E2E 지원 → C-4 Photo Mission** 순으로 진행한다. catalog/seed 내용 확정은 같은 날 병행하고 새로운 아키텍처나 Should/Could 기능은 추가하지 않는다.

### Git/통합 적용 순서

Part C는 다음 순서로 최종 통합한다.

```text
1. 최신 origin/develop을 feature/c-routine-catalog에 최종 반영
2. conflict 확인 및 해결
3. feature/c-routine-catalog 전체 test/build
4. 정상일 경우 feature/c-routine-catalog push
5. 최신 develop에 Part C 전체 최종 merge
6. develop에서 다시 전체 test/build
7. 모두 정상일 때만 origin/develop push
8. feature/c-routine-catalog branch는 삭제하지 않고 보존
9. main에는 아직 merge하지 않음
```

개발 중에는 `feature/c-routine-catalog`에서 C-1~C-6을 이어서 구현한다. 원격 반영과 develop 최종 merge는 Part C 전체 구현 및 로컬 검증이 끝난 통합 단계에서만 수행한다.

---

## 10. 구현 시작 전 확인 목록

업데이트 기준 확인 상태는 다음과 같다.

- [x] Part A의 Bearer token / CurrentUserProvider 구현 확인
- [x] 기본 DailyRoutine materialization interface/implementation 확인
- [x] 날짜별 DailyRoutine 조회 구현 확인
- [x] `feature/c-routine-catalog`에서 C-1 구현
- [x] C-1 unit/controller 계약 테스트와 전체 test/build 통과
- [ ] C-1 실제 PostgreSQL Bearer API smoke/E2E
- [ ] Routine 수정/삭제 ↔ DailyRoutine 재동기화/lock 판정 합의
- [ ] Photo Mission의 DailyRoutine 잠금/할당 수정 파일 합의
- [ ] Story progression read interface 합의
- [ ] 지원 verification object 목록 확정
- [ ] 카테고리별 추천 Routine pool 확정
- [ ] AI 판정 테스트가 끝난 Photo Mission seed 확정
- [ ] 실제 frontend asset이 있는 Item seed 확정
- [ ] StoryEpisode 5개 seed 적용 방식 확인
- [ ] PostgreSQL integration test 방식 확인

완료된 기반 항목은 다시 구현하지 않는다. 특히 token parser, CurrentUserProvider, 기본 materialization, DailyRoutine 조회를 Part C에서 복제하지 않는다.

미확정 항목은 추측해서 채우지 않고 `TBD`로 유지한다.

---

## 11. 각 작업 완료 보고 형식

각 C 단위 완료 후 아래 형식으로 공유한다.

```text
[작업명]

1. 구현한 기능
2. 생성/수정 파일
3. 주요 데이터 흐름
4. API 변경 여부
5. DB 변경 여부
6. 예외 처리
7. 테스트 결과
8. build 결과
9. 아직 남은 TODO
10. 다른 파트가 알아야 할 변경사항
11. commit hash
```

Part C 전체 완료 조건은 개별 endpoint 컴파일이 아니라 Guest 인증 이후 Routine 생성부터 Mission/Item/Story 조회까지 현재 API 계약과 실제 PostgreSQL에서 재현되는 상태다.
