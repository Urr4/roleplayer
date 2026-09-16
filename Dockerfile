# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build the React/Vite frontend
# ─────────────────────────────────────────────────────────────────────────────
FROM mirror.gcr.io/library/node:20-alpine AS frontend-build

WORKDIR /app/frontend
COPY frontend/package*.json ./
# --mount=type=cache persists node's npm cache across builds *independent* of
# layer invalidation, so `npm ci` stays fast even when package-lock.json
# changes (npm still verifies the lockfile, but skips re-downloading tarballs
# already present in the cache).
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Build the Spring Boot jar (frontend already built above — the
# gradle frontend tasks are skipped here so npm doesn't run a second time)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS backend-build

WORKDIR /app
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Prime the Gradle wrapper/dependency cache in its own layer, BEFORE the
# application source is copied in. This is the single biggest lever for
# redeploy speed: previously `RUN ./gradlew bootJar` sat right after
# `COPY src src`, so ANY source change invalidated that layer and reran the
# build against a completely empty Gradle cache — redownloading the Gradle
# distribution itself plus every dependency (Spring Boot, JDA, opus-java,
# ...) from Maven Central on every single redeploy. The `--mount=type=cache`
# below additionally persists ~/.gradle across builds independent of layer
# invalidation (it survives even when build.gradle/settings.gradle change),
# so dependency downloads only happen once, ever, per Pi.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
COPY frontend/package.json frontend/package-lock.json frontend/
COPY --from=frontend-build /app/frontend/dist frontend/dist

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test -x installFrontend -x buildFrontend --no-daemon

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3: Runtime — slim JRE image, ARM64-friendly for the Raspberry Pi
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r roleplayer && useradd -r -g roleplayer roleplayer

# libopus0 provides the native Opus codec JDA's voice support (club.minnced:opus-java)
# falls back to via JNA on architectures (like this image's arm64/Raspberry Pi target)
# for which opus-java ships no bundled native binary — see docs/discord-bot-setup.md.
# ffmpeg is used to remux microphone recordings (see WebmRemuxer.java) — browsers'
# MediaRecorder emits a live recording as a series of independent WebM chunks, and
# naively concatenating their raw bytes produces a file whose duration/seek
# metadata only covers the first chunk (shows 0:00/0:00 and isn't seekable when
# played back) even though the underlying audio/transcription is unaffected.
RUN apt-get update && \
    apt-get install -y --no-install-recommends libopus0 ffmpeg && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend-build /app/build/libs/*-SNAPSHOT.jar app.jar
RUN mkdir -p /data && chown -R roleplayer:roleplayer /app /data

USER roleplayer

ENV PORT=3002
EXPOSE 3002

# -Djava.net.preferIPv4Stack=true: JDA's Discord voice UDP handshake
# (external IP/port discovery) can silently fail/time out when the JVM
# prefers IPv6 sockets on a network where Discord's voice UDP relay only
# resolves reliably over IPv4 — this manifests as the bot joining a voice
# channel and being disconnected almost immediately with
# "ERROR_LOST_CONNECTION"/"The Discord voice connection was lost
# unexpectedly", even though text/REST calls (e.g. sending chat messages)
# work fine since those go over TCP/HTTPS. Forcing IPv4 is the standard fix.
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/app.jar"]
