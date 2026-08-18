# 갓생사자 GitHub 브랜치 운영 가이드

> 문서 버전: v1.0  
> 작성 기준일: 2026-08-19  
> 대상: 갓생사자 백엔드 팀  
> 목적: A-3b 이후 백엔드 병렬 개발에서 충돌과 통합 오류를 줄이고, `main`을 항상 배포 가능한 안정 상태로 유지하기 위한 Git/GitHub 운영 기준

---

## 1. 운영 목표

A-3b 완료 이후 백엔드는 세 파트가 병렬 개발한다.

```text
Part A — Core / Auth / Progression
Part B — AI / Avatar / Speech
Part C — User / Routine CRUD / Catalog / Read API
```

이때 다음 원칙을 지킨다.

1. `main`은 배포 가능한 안정본으로 유지한다.
2. `develop`은 백엔드 전체 기능을 합치는 통합 개발본으로 사용한다.
3. 실제 개발은 `feature/*` 브랜치에서만 진행한다.
4. 기능 하나가 완성될 때마다 `develop`에 작은 단위로 통합한다.
5. `develop`에 기능이 합쳐질 때마다 전체 `test/build`를 다시 실행한다.
6. `main`에는 매 기능마다 올리지 않고, 정의된 Checkpoint가 통합 검증을 통과했을 때만 merge한다.
7. 공통 파일이나 다른 파트 소유 코드를 수정해야 할 경우 먼저 팀에 공유한다.

핵심 원칙은 다음 한 문장으로 정리한다.

> **최신 develop에서 기능 브랜치를 생성 → 기능 개발 → feature에서 test/build → develop에 PR/merge → develop에서 다시 test/build + 통합 테스트 → Checkpoint 완료 시 develop을 main으로 merge → main에서 최종 test/build 후 push**

---

# 2. 브랜치 구조

```text
main
└─ 안정본 / 배포 기준

    ▲
    │ Checkpoint 통합 검증 성공 시만 merge
    │

develop
└─ 백엔드 전체 통합 개발본
    ▲        ▲        ▲
    │        │        │
feature/a-* feature/b-* feature/c-*
Part A       Part B       Part C
```

## 2.1 `main`

역할:

- 언제든 배포 가능한 안정본
- Checkpoint가 완료된 시점의 검증된 코드
- 긴급한 이유가 아니면 직접 개발하지 않음

원칙:

```text
feature → main 직접 merge 금지
개별 기능 완료만으로 develop → main merge 금지
Checkpoint 통합 검증 성공 시만 develop → main merge
```

---

## 2.2 `develop`

역할:

- A/B/C 기능을 실제로 합치는 통합 브랜치
- 전체 `test/build`와 API 통합 테스트를 수행하는 기준 브랜치
- 다음 feature 브랜치를 생성할 때 기준이 되는 브랜치

원칙:

```text
feature/* → develop
```

- 직접 기능 개발은 가급적 하지 않는다.
- merge 직후 깨졌다면 다음 기능을 추가하기 전에 먼저 복구한다.
- 최소한 `test/build`가 통과하는 상태를 유지한다.

---

## 2.3 `feature/*`

역할:

- 실제 개발 작업
- 한 개의 검토 가능한 기능 단위만 포함

권장 예시:

```text
feature/a-check-verification
feature/a-point-claim
feature/a-daily-success
feature/a-photo-orchestration

feature/b-photo-analyzer
feature/b-avatar
feature/b-speech-preset
feature/b-speech-kakao

feature/c-user
feature/c-routine-crud
feature/c-recommendations
feature/c-photo-mission
```

개인당 하나의 거대한 브랜치를 끝까지 유지하는 방식보다 **기능 단위 브랜치를 새로 만드는 방식을 우선한다.**

---

# 3. A-3b 완료 후 최초 세팅

A-3b까지 완료하고 전체 검증이 끝나면 해당 상태를 첫 공통 기준점으로 만든다.

## 3.1 A-3b 검증

```powershell
.\gradlew.bat test
.\gradlew.bat build
git status
```

다음을 확인한다.

```text
[ ] test 성공
[ ] build 성공
[ ] working tree 확인
[ ] 불필요한 파일 없음
[ ] secret / .env / *.pem 없음
[ ] 로컬 업로드/임시 파일 없음
```

## 3.2 `main` 반영

```powershell
git switch main
git pull origin main

git add .
git commit -m "feat: complete daily routine foundation"
git push origin main
```

이미 A-3b 작업이 커밋되어 있다면 불필요하게 새 커밋을 만들지 않고 해당 커밋을 push한다.

## 3.3 `develop` 생성

```powershell
git switch main
git pull origin main

git switch -c develop
git push -u origin develop
```

이 시점부터:

```text
main    = A-3b까지 검증된 안정본
develop = 이후 백엔드 통합 개발본
```

으로 사용한다.

---

# 4. 기능 개발 시작 방법

모든 새 기능은 **최신 `develop`에서 시작한다.**

예시 — Part A가 CHECK 인증 구현 시작:

```powershell
git switch develop
git pull origin develop

git switch -c feature/a-check-verification
```

Part B:

```powershell
git switch develop
git pull origin develop

git switch -c feature/b-photo-analyzer
```

Part C:

```powershell
git switch develop
git pull origin develop

git switch -c feature/c-routine-crud
```

중요:

> 이전에 오래된 `main` 또는 오래된 feature branch에서 새 작업을 시작하지 않는다.

---

# 5. Feature 브랜치 작업 완료 기준

기능 구현이 끝났다고 바로 `develop`에 merge하지 않는다.

먼저 feature branch에서 아래를 수행한다.

```powershell
.\gradlew.bat test
.\gradlew.bat build
git status
```

## 5.1 Feature 완료 체크리스트

```text
[ ] 해당 기능이 완료 조건을 만족하는가
[ ] 관련 성공 케이스 테스트가 있는가
[ ] 주요 실패/예외 케이스 테스트가 있는가
[ ] 전체 test 통과
[ ] 전체 build 통과
[ ] 기존 기능이 깨지지 않았는가
[ ] 다른 담당 파트 기능을 불필요하게 수정하지 않았는가
[ ] 비밀키/비밀번호가 코드에 없는가
[ ] .env / *.pem이 추적되지 않는가
[ ] 사진/ZIP/임시파일/생성 asset이 Git에 들어가지 않았는가
[ ] 디버그 코드와 임시 하드코딩이 남지 않았는가
[ ] 변경 파일을 설명할 수 있는가
```

완료 후:

```powershell
git add .
git commit -m "feat: implement check verification"
git push -u origin feature/a-check-verification
```

GitHub에서 PR 대상은 반드시:

```text
feature/a-check-verification
        ↓
      develop
```

으로 한다.

```text
feature → main 직접 PR 금지
```

---

# 6. Feature → Develop 통합 후 검증

Feature 브랜치에서 테스트가 성공했더라도 `develop`에 merge된 후 다시 검증한다.

이유:

```text
A 기능 단독 성공
B 기능 단독 성공
C 기능 단독 성공

≠

A + B + C 통합 성공
```

## 6.1 Develop 기본 검증

merge 후:

```powershell
git switch develop
git pull origin develop

.\gradlew.bat test
.\gradlew.bat build
```

둘 중 하나라도 실패하면 다음 feature를 merge하기 전에 원인을 해결한다.

---

## 6.2 관련 API Smoke / E2E 테스트

가능한 경우 해당 기능과 연결되는 기존 API까지 실제로 호출한다.

### Routine CRUD merge 예시

```text
POST /sessions
→ POST /routines
→ GET /routines
→ GET /daily-routines
```

### CHECK Verification merge 예시

```text
POST /sessions
→ Routine 생성
→ GET /daily-routines
→ POST /daily-routines/{id}/verifications/check
→ GET /daily-routines
→ COMPLETED 확인
```

### PHOTO 통합 예시

```text
DailyRoutine
→ PhotoMission
→ 사진 업로드
→ PHOTO AI 판정
→ RoutineVerification 저장
→ COMPLETED 확인
```

`develop`은 단순 컴파일용 브랜치가 아니라 **실제 백엔드 통합 검증 브랜치**다.

---

# 7. Develop이 깨졌을 때

merge 직후 `develop`의 test/build가 실패하면 그 상태로 다음 기능을 계속 합치지 않는다.

처리 순서:

```text
최근 merge 확인
→ 실패 원인 확인
→ 해당 담당자와 수정
→ develop에서 test/build 복구
→ 관련 API 재검증
→ 다음 merge 진행
```

원칙:

> **develop은 개발 중인 브랜치지만 최소한 test/build는 통과하는 상태로 복구한 뒤 다음 기능을 합친다.**

---

# 8. Main 승격 Checkpoint

개별 기능이 끝날 때마다 `main`에 merge하지 않는다.

현재 백엔드는 다음 Checkpoint를 기준으로 `develop → main` 승격을 진행한다.

---

## Checkpoint 0 — 공통 기반

### 완료 범위

```text
PostgreSQL / Flyway
Session / Guest Auth
Bearer Token
CurrentUserProvider
DailyRoutine Materialization
GET /daily-routines
```

### 핵심 확인

```text
POST /sessions 동작
Bearer 인증 동작
Flyway V1~V4 적용
DailyRoutine 생성/조회 동작
전체 test/build 성공
```

A-3b까지의 상태를 첫 `main` 안정본으로 사용한다.

---

## Checkpoint 1 — Routine Core E2E

### 필요한 기능

```text
C: Routine CRUD
A: DailyRoutine
A: CHECK Verification
```

### 필수 E2E

```text
POST /sessions
→ POST /routines
→ GET /daily-routines
→ POST /daily-routines/{id}/verifications/check
→ GET /daily-routines
→ COMPLETED 확인
```

### 승격 조건

```text
[ ] Routine 생성 성공
[ ] DailyRoutine materialization 성공
[ ] CHECK 인증 성공
[ ] 인증 이후 COMPLETED 반환
[ ] 타 사용자 Resource 접근 차단
[ ] test 성공
[ ] build 성공
```

전부 통과하면:

```text
develop → main
```

---

## Checkpoint 2 — 핵심 인증/보상 Loop

### 필요한 기능

```text
B: PHOTO AI Analyzer
C/B: Photo Mission 연동
A: PHOTO Verification orchestration
A: Point Claim
A: DailySuccess
A: Item Unlock
A: Story Unlock
```

### 핵심 E2E

```text
Routine 생성
→ DailyRoutine 생성
→ Photo Mission
→ PHOTO 또는 CHECK 인증
→ COMPLETED
→ Point Claim
→ Point 반영
→ 조건 충족 시 Item / Story 반영
```

### 반드시 검증할 정책

```text
PHOTO Claim = 10P
CHECK Claim = 5P
serviceDate당 최대 3개 Claim
동일 DailyRoutine 중복 Claim 금지
TO_DO Point Claim 금지
Verification 성공 시 Point 자동 지급 금지
```

### 승격 조건

```text
[ ] PHOTO 정상 흐름
[ ] CHECK 정상 흐름
[ ] Point Claim 정상
[ ] 중복/제한 검증 정상
[ ] DailySuccess 정상
[ ] Item/Story 처리 정상
[ ] test 성공
[ ] build 성공
```

전부 통과하면:

```text
develop → main
```

---

## Checkpoint 3 — Onboarding / 개인화 통합

### 필요한 기능

```text
Guest Session
User nickname
Avatar
Speech Style
Home dependency 연결
```

### 핵심 E2E

```text
POST /sessions
→ PATCH /users/me
→ PUT /avatars/me
→ Speech preset 또는 Kakao 설정
→ GET /home
```

### 반드시 검증

```text
[ ] Authenticated User 기준 데이터 연결
[ ] Avatar 설정 성공
[ ] Avatar AI 실패 시 정의된 fallback 동작
[ ] Speech Style 설정 성공
[ ] 초기 설정 미완료 시 Home 차단
[ ] 초기 설정 완료 시 Home 진입
[ ] test 성공
[ ] build 성공
```

전부 통과하면:

```text
develop → main
```

---

## Checkpoint 4 — MVP Backend 완성

### 최종 흐름

```text
Onboarding
→ Routine CRUD
→ DailyRoutine
→ PHOTO/CHECK Verification
→ Point Claim
→ Item/Story
→ Avatar Stage
→ Records
→ Competition
→ Home
```

### 최종 Read API 확인

```text
GET /home
GET /records
GET /stories
GET /items
GET /competition/leaderboard
```

### 승격 조건

```text
[ ] 핵심 사용자 흐름 E2E 성공
[ ] 전체 test 성공
[ ] 전체 build 성공
[ ] DB/Flyway 오류 없음
[ ] 외부 API 오류 처리 확인
[ ] 핵심 API에서 치명적 500 오류 없음
[ ] secret/private 파일 없음
[ ] 데모에 필요한 API 흐름 실행 가능
```

통과한 `main`을 배포 기준으로 사용한다.

---

# 9. Develop → Main 승격 절차

가능하면 `develop → main` 승격은 한 명의 통합 담당자가 관리한다.

## 9.1 Develop 최종 확인

```powershell
git switch develop
git pull origin develop

.\gradlew.bat test
.\gradlew.bat build
```

그리고 해당 Checkpoint의 E2E를 직접 확인한다.

## 9.2 Main merge

검증 성공 후:

```powershell
git switch main
git pull origin main

git merge --no-ff develop
```

## 9.3 Main에서 다시 최종 확인

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

둘 다 성공한 경우에만:

```powershell
git push origin main
```

즉:

```text
develop test/build + E2E
→ main merge
→ main test/build
→ push
```

순서를 지킨다.

---

# 10. Main 승격 공통 체크리스트

`develop → main` 전 다음을 모두 확인한다.

```text
[ ] develop이 최신 상태인가
[ ] develop 전체 test 통과
[ ] develop 전체 build 통과
[ ] 해당 Checkpoint API E2E 성공
[ ] 기존 기능 regression 없음
[ ] DB/Flyway 오류 없음
[ ] Entity/Flyway에 합의되지 않은 변경 없음
[ ] secret/API key/DB password 없음
[ ] .env / *.pem 없음
[ ] Kakao 원본 ZIP/TXT 없음
[ ] Verification 원본 사진 없음
[ ] Avatar 원본 얼굴 사진 없음
[ ] 임시 upload/storage 파일 없음
[ ] 불필요한 디버그/하드코딩 없음
[ ] 치명적 500 오류 없음
```

한 항목이라도 실패하면 `main`으로 올리지 않고 `develop`에서 수정한다.

---

# 11. 최신 Develop을 Feature에 반영하는 방법

다른 팀원의 기능이 `develop`에 merge되었다고 매번 작업 중인 branch에 합칠 필요는 없다.

## 권장 방식

현재 기능을 완료해서 `develop`에 merge한 다음, 다음 작업을 시작할 때 최신 `develop`에서 새 branch를 만든다.

```powershell
git switch develop
git pull origin develop

git switch -c feature/a-next-feature
```

## 작업 중 반드시 최신 코드가 필요한 경우

```powershell
git switch feature/a-current-feature
git merge develop
```

팀이 rebase에 익숙하지 않다면 MVP 기간에는 merge 방식을 우선한다.

---

# 12. 파일 충돌 방지 규칙

아래 파일은 여러 사람이 동시에 수정할 경우 충돌 또는 구조 변경 위험이 크다.

```text
src/main/resources/application.yaml
build.gradle
AGENTS.md
docs/**
Flyway V1~V4
common/**
common/auth/**
공통 ErrorCode / Exception 구조
기존 Entity schema
```

이 파일을 변경해야 한다면:

```text
변경 필요 발견
→ 팀 채팅 공유
→ 변경 목적/범위 확인
→ 담당자 확인
→ 수정
```

합의 없이 공통 구조를 바꾸지 않는다.

---

# 13. 파트별 소유권 경계

## Part A — Core / Auth / Progression

주요 소유:

```text
session/**
common/auth/**
routine/daily/**
routine/verification/** orchestration
routine/point/**
DailySuccess
Item Unlock
Story Unlock
record/**
competition/**
home/**
```

PHOTO 전체 AI 판정 구현을 직접 소유하지 않는다.

---

## Part B — AI / Avatar / Speech

주요 소유:

```text
avatar/**
speech/**
PHOTO AI Analyzer
```

권장 경계:

```text
PhotoVerificationAnalyzer
→ 사진으로 물건/gesture/mission 판정

RoutineVerificationService
→ Part A가 인증 시간/권한/저장/DailySuccess orchestration 담당
```

같은 Verification Service를 A/B가 동시에 수정하지 않는다.

---

## Part C — User / Routine CRUD / Catalog / Read

주요 소유:

```text
user/**
routine/**
단 routine/daily, routine/verification, routine/point 제외
item/** read
story/** read
```

Routine CRUD는 별도의 DailyRoutine 생성 로직을 만들지 않고 A가 제공한 `DailyRoutineMaterializationService`를 사용한다.

---

# 14. 공통 인증 사용 규칙

A-2 이후 인증은 이미 공통 기반으로 제공된다.

각 도메인은:

```text
Authorization: Bearer <token>
→ GuestSession
→ CurrentUserProvider
```

를 사용한다.

금지:

```text
request body userId로 소유권 판단
query userId로 인증 대체
path userId를 현재 사용자로 신뢰
각 파트별 token parser 재구현
JWT 임의 도입
```

현재 사용자 정보가 필요하면 기존 `CurrentUserProvider`를 사용한다.

---

# 15. Commit / PR 단위

좋은 merge 단위:

```text
Guest Session/Auth
Routine CRUD
DailyRoutine Query
CHECK Verification
PHOTO Analyzer
Point Claim
DailySuccess
Avatar Default/Storage
Speech Preset
Kakao Parsing
```

너무 작은 단위:

```text
Repository method 하나
DTO 하나
미완성 Service 절반
테스트가 없는 임시 구현
```

너무 큰 단위:

```text
Part B 전체를 한 PR
Routine + Avatar + Speech를 한 PR
여러 도메인의 무관한 기능을 한 번에 수정
```

기준:

> **한 번에 리뷰하고 test/build 후 되돌릴 수 있는 하나의 기능 단위**

---

# 16. 권장 Commit Message 예시

```text
feat: implement guest session authentication
feat: implement daily routine materialization
feat: implement routine CRUD
feat: implement check verification
feat: implement photo verification analyzer
feat: implement routine point claim
feat: implement daily success evaluation
feat: implement avatar storage fallback
feat: implement speech preset flow
fix: prevent duplicate routine verification
fix: handle expired point claim
```

---

# 17. 팀원 공유용 요약

팀 단톡에는 아래처럼 공유하면 된다.

> A-3b 완료 후 백엔드 Git 운영은 `main / develop / feature` 3단계로 진행합니다.  
> `main`은 배포 가능한 안정본, `develop`은 전체 통합본, 실제 개발은 각 `feature/*` 브랜치에서 진행합니다.  
> 모든 새 feature는 최신 `develop`에서 생성해주세요. 기능 완료 후 본인 branch에서 `test/build` 통과 → `develop`으로 PR/merge → `develop`에서 다시 전체 `test/build`와 관련 API 통합 테스트를 진행합니다.  
> `main`은 기능 하나 끝날 때마다 올리지 않고, 정해둔 Checkpoint의 E2E가 통과했을 때만 `develop → main`으로 merge합니다.  
> 공통 파일이나 다른 파트 소유 파일을 수정해야 하는 경우 먼저 공유해주세요.

---

# 18. 최종 운영 원칙

```text
1. 최신 develop에서 feature branch 생성
2. feature에서 기능 하나 구현
3. feature에서 test/build
4. commit + push
5. feature → develop PR/merge
6. develop 전체 test/build
7. 관련 API smoke/E2E
8. 문제 있으면 develop에서 먼저 복구
9. Checkpoint 완성 시 develop → main
10. main에서 test/build 재검증
11. 성공 시 main push / 배포 기준 확정
```

가장 중요한 원칙은 다음 두 가지다.

> **feature → develop은 자주 한다.**

> **develop → main은 통합 검증된 Checkpoint에서만 한다.**
