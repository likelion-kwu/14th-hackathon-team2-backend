# Docker 실행 가이드

이 구성은 Spring Boot 백엔드와 PostgreSQL 16을 컨테이너로 실행한다. 운영 배포도 GitHub Actions가 빌드한 이미지를 가비아 VM에 전송한 뒤 같은 Compose 스택으로 교체한다.

## 구성

```text
host 127.0.0.1:${APP_PORT}
        ↓
backend (Java 17, non-root UID 10001)
        ↓
postgres (PostgreSQL 16)
```

영속 데이터는 두 위치에 나뉜다.

- PostgreSQL 데이터: Docker named volume `postgres-data`
- Avatar Stage PNG: `AVATAR_HOST_PATH`의 호스트 디렉터리

Kakao 분석 임시 데이터는 컨테이너의 512MB tmpfs에 저장하며 컨테이너 재생성 시 남지 않는다.

## 로컬 실행

Docker Engine과 Docker Compose가 필요하다.

```bash
cp .env.example .env
mkdir -p data/avatars
docker compose config
docker compose up --build -d
curl --fail http://127.0.0.1:8080/api/v1/health
```

`.env`의 `POSTGRES_PASSWORD`는 실행 전에 반드시 변경한다. AI 기능을 실제로 확인하려면 `OPENAI_API_KEY`도 설정한다.

`compose.yaml`의 PostgreSQL은 새 named volume을 사용한다. 기존 서버 DB의 데이터가 자동으로 복사되거나 이전되지는 않는다. 기존 DB를 사용하는 운영 환경을 전환할 때는 DB 연결 방식과 백업·복구 절차를 별도로 확정해야 한다.

로그와 종료 명령:

```bash
docker compose logs -f backend
docker compose down
```

`docker compose down`은 PostgreSQL named volume과 Avatar 호스트 파일을 보존한다. `docker compose down --volumes`는 PostgreSQL 데이터를 삭제하므로 초기화 의도가 없으면 사용하지 않는다.

같은 호스트에서 기존 JAR 서비스와 Docker backend를 동일한 `APP_PORT`로 동시에 실행할 수 없다. 실행 경로를 전환할 때는 한쪽만 해당 포트를 사용하도록 한다.

## Gabia VM Avatar 디렉터리

문서에서 확정한 호스트 경로를 사용할 경우 컨테이너 UID 10001이 쓸 수 있도록 먼저 준비한다.

```bash
sudo install -d -m 0700 -o 10001 -g 10001 /var/lib/godsaengsaja/avatars
```

그 후 `.env`에 다음을 설정한다.

```dotenv
AVATAR_HOST_PATH=/var/lib/godsaengsaja/avatars
```

앱 내부의 `AVATAR_STORAGE_ROOT`는 Compose가 `/app/data/avatars`로 고정한다. DB에는 호스트 절대 경로가 아니라 논리적인 `asset_set_key`만 저장된다.

## 주요 환경변수

| 변수 | 설명 | 기본/예시 |
|---|---|---|
| `APP_VERSION` | 이미지와 애플리케이션 버전 | `0.0.2-SNAPSHOT` |
| `APP_BIND_ADDRESS` | 호스트 bind 주소 | `127.0.0.1` |
| `APP_PORT` | 호스트 HTTP 포트 | `8080` |
| `POSTGRES_DB` | PostgreSQL DB 이름 | `godsaeng_lion` |
| `POSTGRES_USER` | PostgreSQL 사용자 | `godsaeng` |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 | 필수 변경 |
| `AVATAR_HOST_PATH` | Avatar PNG 호스트 디렉터리 | `./data/avatars` |
| `OPENAI_API_KEY` | OpenAI API 키 | 선택, Git 커밋 금지 |

## 검증 항목

컨테이너 구성을 변경할 때 다음을 확인한다.

1. `docker compose config`가 성공한다.
2. 이미지는 Java 17로 빌드되고 컨테이너가 UID 10001로 실행된다.
3. PostgreSQL health check 이후 Flyway V1~V5가 적용된다.
4. `/api/v1/health`가 200을 반환한다.
5. 컨테이너 재생성 후 PostgreSQL 데이터와 Avatar 파일이 유지된다.
6. `/app/tmp`의 Kakao 임시 데이터는 영속 볼륨에 저장되지 않는다.

현재 health API는 애플리케이션 프로세스의 liveness만 확인하며 DB/OpenAI까지 호출하는 readiness check는 아니다.

## 운영 자동 배포

`main`에 push되면 GitHub Actions가 테스트와 Docker 이미지 빌드를 수행하고, 이미지 archive와 `compose.yaml`을 SSH로 가비아 VM에 전송한다. 서버는 `/opt`, `/srv`, 또는 `/home` 아래의 `GABIA_DEPLOY_PATH`에 Compose 파일과 `.env`를 유지한다.

배포 시 기존 PostgreSQL named volume과 Avatar 호스트 디렉터리는 보존된다. 새 backend 컨테이너가 health check를 통과하지 못하면 직전 이미지로 되돌린다. Flyway가 이미 적용한 DB migration은 이미지 롤백으로 되돌아가지 않으므로 운영 migration은 직전 버전과 호환되어야 한다.

서버의 `${GABIA_DEPLOY_PATH}/.env`에는 최소한 강한 `POSTGRES_PASSWORD`를 설정해야 한다. 이 파일은 GitHub artifact나 저장소에 포함하지 않고 서버에만 `0600` 권한으로 둔다.
