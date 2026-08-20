# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean bootJar --no-daemon \
    && cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

ARG APP_VERSION=0.0.2-SNAPSHOT

LABEL org.opencontainers.image.title="godsaeng-lion-backend" \
      org.opencontainers.image.description="Godsaeng Lion Spring Boot backend" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /home/app --create-home app \
    && mkdir -p /app/data/avatars /app/tmp \
    && chown -R app:app /app /home/app

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/app.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/app/tmp" \
    SPRING_PROFILES_ACTIVE="prod" \
    AVATAR_STORAGE_ROOT="/app/data/avatars" \
    SPEECH_WORK_ROOT="/app/tmp/speech"

USER 10001:10001

EXPOSE 8080

STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
