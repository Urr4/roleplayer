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
RUN apt-get update && \
    apt-get install -y --no-install-recommends libopus0 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend-build /app/build/libs/*-SNAPSHOT.jar app.jar
RUN mkdir -p /data && chown -R roleplayer:roleplayer /app /data

USER roleplayer

ENV PORT=3002
EXPOSE 3002

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
