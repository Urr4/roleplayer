# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build the React/Vite frontend
# ─────────────────────────────────────────────────────────────────────────────
FROM mirror.gcr.io/library/node:20-alpine AS frontend-build

WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
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
COPY src src
COPY frontend/package.json frontend/package-lock.json frontend/
COPY --from=frontend-build /app/frontend/dist frontend/dist

RUN chmod +x gradlew && \
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
