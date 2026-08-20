# AGENTS.md

> 2026-08-19 sync check: TO_DO progression exclusion and the Must monthly record calendar are current agreed policies. The PRD / common prompt / DB design / API spec copies delivered with this file are synchronized to them; the speech SRS remains v2.7 because this calendar change does not alter speech behavior.

## Purpose

This repository is the backend for **갓생사자**, a 2026 멋쟁이사자처럼 중앙해커톤 AAC-linked MVP.
The goal is to ship a stable, externally accessible MVP with the Must user flow working end to end by **2026-08-21**.

Codex must optimize for:

1. correctness of the current agreed product rules,
2. finishing the Must flow,
3. testability and demo stability,
4. minimal architecture and minimal new dependencies,
5. safe handling of photos, chat exports, secrets, and external AI failures.

Do not broaden scope with Should/Could features while Must behavior is incomplete.

---

## Source of truth

Before implementing a domain, read the relevant current document. Expected repository paths are:

```text
docs/갓생사자_PRD_v2.2.md
docs/갓생사자_API_SPEC_v4.4.md
docs/갓생사자_backend_database_design_v1.9.md
docs/speech_style_system_SRS_v2.7.md
docs/project_common_prompt_v4.1.md
```

Authority by concern:

- product behavior and scope: `갓생사자_PRD_v2.2.md`
- HTTP contract and error behavior: `갓생사자_API_SPEC_v4.4.md`
- DB source-of-truth, constraints, transactions, locking: `갓생사자_backend_database_design_v1.9.md`
- speech-style/Kakao behavior: `speech_style_system_SRS_v2.7.md`
- general project context and implementation guardrails: `project_common_prompt_v4.1.md`

If a required document is missing from the repository, report it instead of inventing the missing policy.
If two current documents conflict, do not silently reconcile them: report the conflict before changing behavior.
Historical changelog text in older-version sections is not current policy. Prefer the latest current sections and explicit Freeze rules.

---

## Current repository facts

Re-check these before making structural changes, because the repository may evolve.

Observed baseline:

```text
Build: Gradle Wrapper
Language: Java 17 toolchain
Framework: Spring Boot 4.1.0
Container: Docker multi-stage Java 17 image + Docker Compose
Base package: com.likelion.hackathon_be
Web: Spring Web MVC
Persistence: Spring Data JPA
Migration: Flyway
Database driver: PostgreSQL
Validation: Jakarta/Spring validation starter
Boilerplate: Lombok
```

The repository began as a minimal Spring scaffold. Do not assume package architecture, BaseEntity patterns, exception conventions, DB configuration, Docker configuration, or deployment configuration that you have not actually found in the current code.

---

## Required workflow for Codex

### Before coding

For any non-trivial feature, DB migration, external API integration, authentication change, or architecture change:

1. inspect the current repository and related source files,
2. read the relevant current project docs,
3. summarize the existing structure,
4. state the implementation plan,
5. list files to create/modify,
6. describe data flow and transaction boundaries,
7. list important failure cases and risks,
8. state the tests/build commands to run.

For large work, stop at the plan when the user asked for planning/review only. Do not implement unrelated domains in the same change.

### During coding

- Follow existing repository patterns once they exist.
- Keep controllers thin; put domain rules in services.
- Do not call OpenAI or another external service inside a DB transaction.
- Validate ownership and input server-side.
- Do not trust client-provided `userId`, `ownerId`, service time, reward amount, stage, streak, or unlock state.
- Prefer DB UNIQUE constraints plus transactional logic over `exists()` checks alone.
- Keep changes reviewable and scoped to the requested task.

### After coding

1. run relevant tests,
2. run a full build when practical,
3. inspect the diff for unrelated changes,
4. verify no secret, raw chat text, uploaded photo, generated temporary file, or local environment file is tracked,
5. summarize changed files and behavior,
6. state remaining risks/TODOs,
7. confirm whether the work is commit-ready.

---

## Build and test commands

Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Linux/macOS/server:

```bash
./gradlew test
./gradlew build
```

Do not invent lint/format tasks that are not in `build.gradle`.
If tests require PostgreSQL or other infrastructure, inspect the current test setup before adding H2, Testcontainers, Docker, or another testing dependency.

---

## Core product invariants

### Session and onboarding

MVP uses guest sessions, not full signup/OAuth.

```text
Guest session
→ nickname
→ Avatar
→ SpeechStyle
→ Home
```

The backend must derive the authenticated user from the guest token/session. Never authorize a resource using a user ID supplied by the client.

### Routine categories and repeat types

Routine categories are exactly:

```text
SKIN
WELL_BEING
HEALTH_FIT
DIET
TO_DO
```

Rules:

```text
SKIN / WELL_BEING / HEALTH_FIT / DIET
→ DAILY or DAYS_OF_WEEK

TO_DO
→ ONCE only
→ one specific scheduled date
```

Do not add cross-midnight routines in MVP.
Require `startTime < endTime`; `24:00` is unsupported and the last representable minute is `23:59`.

### TO_DO progression policy

`RoutineCategory.TO_DO` is a one-time auxiliary task.

- TO_DO may create a DailyRoutine and may be completed/verified.
- TO_DO MUST NOT be included in today's routine progress.
- TO_DO MUST NOT contribute to DailySuccess.
- TO_DO MUST NOT affect Story streak progression.
- TO_DO MUST NOT be eligible for RoutinePointClaim.
- Therefore TO_DO cannot contribute to Item unlock or Competition points.
- Do not implement these exclusions by adding duplicated counters or status columns.
  Derive them in service logic from Routine.category.

### Routine recommendations

MVP recommendations come from a server-side, pre-reviewed pool (code/JSON/YAML), normally returning 3 candidates for the selected category.

Do not add real-time AI routine generation in MVP.
A recommendation fills/suggests the form; it must not save or modify a routine without user confirmation.

### Monthly record calendar (Must)

The monthly record calendar is part of the MVP Must flow. Do not add a dedicated Calendar table, persistent calendar status, monthly snapshot, or separate Calendar API for it. Use `GET /api/v1/records` for the displayed month (maximum 31 days).

Progression eligibility is the same as the rest of the product: `categorySnapshot != TO_DO`. `TO_DO` may appear in the underlying routine history but does not affect the calendar progression state.

Calendar display contract:

```text
totalCount == 0
→ blank (including TO_DO-only dates)

past date + totalCount > 0 + completedCount == 0
→ red X

0 < completedCount < totalCount
→ yellow -

completedCount == totalCount && totalCount > 0
→ green check

future date
→ blank
```

For today, `completedCount == 0` remains neutral/blank until the existing Record `dayStatus` becomes `FAILED`; only then show the red X. Partial completion is yellow and full completion is green regardless of the failure status of remaining routines.

`N days achieved this month` means the number of green dates in the displayed month, equivalently the number of `DailySuccessRecord` rows in that month. Do not use the all-time `totalSuccessDays` field for this monthly count. Previous and next month navigation are allowed; future dates remain blank.

### DailyRoutine source of truth

- completion: successful `RoutineVerification` exists
- verification mode: `RoutineVerification.verification_type`
- day success: `DailySuccessRecord`
- do not add a persistent `DailyRoutine.status`

After the first successful verification for a `serviceDate`, do not mutate that date's DailyRoutine set through routine CRUD.
Already-started routines must not be retroactively removed from the current day's denominator.

### PHOTO / CHECK verification

One DailyRoutine can be completed only once:

```text
PHOTO XOR CHECK
```

Both verification modes count equally for routine completion and day success.
They differ only in the Point amount available when the user later claims the completed routine.

Verification is allowed only inside the server-evaluated routine window.
For PHOTO, capture the request-received time before the AI call; a request received before the end may finish AI processing after the end and still succeed.
A new request received after the end must not start AI processing.

Original verification photos are temporary and must be deleted after processing.

### Point Claim

Verification must **not** auto-award Point.
The user claims Point by tapping a completed routine on the same `serviceDate`.

```text
PHOTO verification → claim 10P
CHECK verification → claim 5P
maximum 3 claimed routines per user per serviceDate
unclaimed Point expires when that serviceDate ends
```

Source of truth is `RoutinePointClaim`.
The client never sends the awarded amount.
Do not implement wallet, spend, balance, purchase, bet, boost, Battle Pass, or Point deduction behavior.

Derived values:

```text
total earned Point = SUM(RoutinePointClaim.amount)
monthly competition Point = SUM claims in the Asia/Seoul calendar month
```

### Item unlock and equipment

Remove/ignore all historical rules that unlock items from cumulative successful days.
Current Item unlock rule:

```text
100P, 200P, 300P, ... total earned Point
→ randomly unlock one currently unowned active Item
→ Point is not deducted
```

Persist processed milestones so the same milestone cannot reward twice.

Equipment source of truth is `UserItem.equipped`.
Multiple items may be equipped at the same time.
Item PNG binaries are frontend static assets, not backend-uploaded files.
All item assets use the same transparent 250×500 canvas as the Avatar and are overlaid by the frontend.

Do not invent backend `slot`, `x`, `y`, or `layerOrder` columns.
If deterministic frontend render order becomes necessary, report the UI/design requirement instead of changing the DB without agreement.

### Competition

Competition is a read-only monthly ranking of Point earned during that month.

```text
ranking metric = monthly RoutinePointClaim sum
Point spending = none
ties = shared rank
rank example = 1, 2, 2, 4
```

Do not create a persistent leaderboard table unless a measured performance problem requires it.

### Story and Avatar Stage

Story unlock is based on consecutive full-day success:

```text
7 → EP.1
14 → EP.2
21 → EP.3
28 → EP.4
35 → EP.5
```

Story unlock is permanent.

Avatar Stage is derived from unlocked Story, never from XP/Point and not stored as a mutable Avatar stage column:

```text
no Story → Stage 1
EP.1 → Stage 2
EP.2+ → Stage 3
```

EP.3~EP.5 remain Stage 3.

---

## Avatar image rules

Avatar growth track is selected once during onboarding and cannot be changed in MVP:

```text
SKIN
WELL_BEING
HEALTH_FIT
DIET
```

`TO_DO` is not an Avatar growth track.

Track visual progression:

```text
SKIN        → skin expression improves over 3 stages
WELL_BEING  → complexion becomes more lively over 3 stages
HEALTH_FIT  → facial puffiness reduces over 3 stages
DIET        → facial puffiness reduces over 3 stages
```

Do not change body shape. These visuals are game-like progression, not diagnosis or prediction of real health/appearance.

### Face photo

Face photo is optional.
If present, use it only as a reference for facial identity/character resemblance.
Do not analyze actual skin condition, health, complexion, or facial swelling to determine the Stage.
Do not extract glasses/clothing/accessories from the photo.

Input guidance is one visible face, near-frontal, sufficiently unobstructed.
Do not introduce a complex face-analysis subsystem solely to enforce this. Keep validation proportional to MVP needs.

The original face photo is temporary and must be deleted on every success/failure/fallback path.
Never store it in the DB or permanent avatar directory.

### Stage generation

Initial setup prepares all three images:

```text
fixed template + track + optional face reference
→ Stage 1
→ Stage 1 as reference → Stage 2
→ Stage 1 as reference → Stage 3
```

Stage 2 and Stage 3 must remain the same character, canvas, pose, and body position.
The visual difference should be noticeable without turning Stage 1 into an insulting or medically framed appearance.

Final stored format:

```text
250×500 px
PNG
RGBA transparent background
same canvas / pose / body position
```

### Avatar storage

Generated Stage PNGs are stored on the Gabia VM host disk, not as DB BLOBs and not only inside an ephemeral Docker container filesystem.

Use a configurable root such as:

```text
AVATAR_STORAGE_ROOT
```

DB stores only the logical active `asset_set_key`.
Never expose host absolute filesystem paths through the API.
Protect path resolution against traversal.

When using Docker, mount a persistent host directory into the container.

### Regeneration and failure

- initial generation: retry failed generation once; if the set still cannot be created, attach the track-specific DEFAULT Stage set and continue onboarding
- regeneration: one **successful** regeneration is allowed during onboarding
- regeneration replaces Stage 1/2/3 as a set, never one stage independently
- keep the previous active set until the entire new set succeeds
- after DB switch succeeds, delete the old generated set
- failed regeneration keeps the old set and does not consume the successful-regeneration count

Do not expose future Stage image assets before Story unlock. The authenticated image endpoint serves the current derived Stage only.

Do not add an Avatar generation job/table unless synchronous generation is proven unusable in the deployed environment and the team explicitly accepts the scope change.

---

## Speech-style rules

For speech-style work, read `speech_style_system_SRS_v2.7.md` before coding.

Key constraints:

- Kakaotalk ZIP or preset: exactly one active method
- analyze only the selected user's messages; other-party text is context only
- valid user messages must meet the SRS minimum before analysis
- raw ZIP/TXT/full parsed chat/context are temporary
- raw chat content must not be logged
- long-term storage is limited to structured style/profile data, examples, generated dialogues, settings, and required metadata
- 8 situations × 5 pre-generated avatar dialogues = 40
- short avatar dialogue maximum length = 50 characters including spaces
- speech reset deletes speech-related data only; Avatar/Routine/Verification/Point/Item/Story data remain

Never run user-specific fine-tuning or automatic background relearning in MVP.

---

## AI and external API rules

AI is used for agreed product roles such as:

- speech-style analysis and dialogue generation
- PHOTO object/gesture verification
- optional face-reference Avatar image generation/editing

Do not add AI merely because a feature can use it.
Do not add real-time AI routine recommendations, medical diagnosis, actual skin scoring, actual future-face prediction, or guaranteed health outcomes.

External API rules:

- credentials only through server environment/configuration
- never expose API keys to frontend or responses
- do not log complete OpenAI requests/responses when they contain user chat or photos
- external calls outside DB transactions
- handle timeout, invalid response, and provider failure explicitly
- provide documented fallback behavior where the product spec defines one

---

## DB and migration rules

- PostgreSQL is the current DB target.
- Use Flyway for schema migrations once migration structure is established.
- JPA enums must be stored as strings, never ordinals.
- Do not use destructive `create-drop` behavior for shared/deployed DBs.
- Do not initialize/drop/reset production or shared data without explicit human confirmation.
- Do not add duplicate counters when the value is reproducible from the documented source of truth.

Important sources of truth:

```text
Routine completion      = RoutineVerification existence
Day success             = DailySuccessRecord
Point awarded           = RoutinePointClaim
Item ownership          = UserItem
Item equipment          = UserItem.equipped
Story unlock            = UserStoryUnlock
Avatar current Stage    = max unlocked Story avatar stage, else 1
Avatar active asset set = Avatar.asset_set_key
```

For Routine completion / Point claim concurrency, follow the DB design's lock order. User-level serialization and DB UNIQUE constraints are intentional MVP safety mechanisms; do not remove them as an optimization without tests and agreement.

---

## Security, privacy, and filesystem safety

Never commit or log:

```text
.env files
API keys
DB passwords
private keys / *.pem
Kakao raw ZIP/TXT/chat content
verification photo originals
Avatar source face photos
local generated/temp upload directories
```

Before committing deployment/config changes, verify `.gitignore` covers local secret files and generated/uploaded assets.

For uploads:

- enforce size/type limits
- use server-generated temporary names
- prevent Zip Slip/path traversal
- clean up in `finally`/equivalent guaranteed paths
- do not trust original filenames for filesystem placement

Health/skin wording must not claim diagnosis, guaranteed treatment, guaranteed improvement, or replacement of professional medical care.

---

## Environment variables

Currently agreed persistent names include:

```text
OPENAI_API_KEY
AVATAR_STORAGE_ROOT
```

Do not invent permanent DB/deployment environment-variable names until current deployment configuration is inspected and the team chooses them.
When new required environment variables are finalized, update this file and the deployment documentation in the same change.

---

## Files and changes requiring human confirmation

Do not perform these as an incidental change:

- deleting data or resetting the DB
- destructive Flyway migrations
- changing production infrastructure or Gabia VM storage/mounts
- rotating or editing secret keys
- changing the Avatar storage root in production
- changing core Product/DB/API policy from the current docs
- adding a new external paid service or a large production dependency

Explain the intended command/change and impact first.

---

## Definition of done

A backend task is not done only because code compiles.
For the requested scope, verify as applicable:

- API behavior matches the current API spec
- DB changes match DB design and have a migration
- validation and ownership checks exist
- duplicate/concurrent requests are safe
- external API failure paths are handled
- temporary/private input is deleted as required
- no secret or private file is tracked
- tests cover core success and important failure paths
- `gradlew test` passes
- `gradlew build` passes or any blocker is explicitly reported
- no unrelated files changed
- documentation is updated when an agreed contract changed
- final diff is small enough to review and is commit-ready

---

## Code review rules

Reject or flag changes that introduce any of the following without an explicit new product decision:

- XP/Experience/Coin or Point spending/wallet balance
- Item unlock based on cumulative successful days
- automatic Point award during verification
- more than 3 Point claims per serviceDate
- cross-midnight Routine support
- mutable DB Avatar Stage counter
- backend Item overlay coordinates/slot/layerOrder
- storing Avatar images as DB BLOBs
- storing original Avatar face photos or verification photos long term
- exposing future Avatar Stage assets before unlock
- AI-based real skin/health diagnosis or future appearance prediction
- raw Kakao chat/photo logging
- external AI calls inside a DB transaction
- authorization based on client-supplied user IDs
- destructive DB/deployment/secret changes without human confirmation

---

## Current priority

Until the MVP is stable, prioritize the shortest complete path through:

```text
guest session
→ nickname
→ Avatar
→ speech style
→ Routine create/read/update/delete + recommendations/TO_DO
→ DailyRoutine
→ PHOTO/CHECK verification
→ Point Claim
→ Item unlock/equipment
→ Day success + Story unlock + Avatar Stage
→ records + Must monthly calendar / competition
→ deployment + demo data + failure handling
```

If time becomes constrained, remove/simplify Should/Could work before changing these core invariants.
