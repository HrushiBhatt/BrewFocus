# syntax=docker/dockerfile:1
# One image for the whole app: Spring Boot serves the API and the built React app.
# It's a multi-stage build, so Node, Maven and the JDK are used to build but never shipped.
# Base images are pinned by digest, so a build can't silently pick up a replaced image.
# Dependabot proposes new digests weekly (.github/dependabot.yml).

# 1. Build the React frontend.
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar. Dependencies are fetched in their own layer, so a code change
# doesn't download them again.
FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS api
WORKDIR /api
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
RUN ./mvnw -q -B -DskipTests package && mkdir /data

# 3. The runtime: a JRE and the jar, running as a non-root user. Distroless has no shell or package
# manager for an attacker to use.
FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d
COPY --from=api /api/target/brew-focus.jar /app/brew-focus.jar
COPY --from=api --chown=nonroot:nonroot /data /data
COPY --from=web /web/dist /app/static
# The prod profile refuses to start without a strong JWT_SECRET. Set SECURE_COOKIES=false only to
# try the image over plain HTTP in a browser that won't send Secure cookies to localhost.
ENV SPRING_PROFILES_ACTIVE=prod STATIC_DIR=/app/static DB_PATH=/data/brewfocus.db SECURE_COOKIES=true
VOLUME /data
EXPOSE 5001
# Size the heap from the container's memory limit, not the host's.
ENTRYPOINT ["/usr/bin/java", "-XX:MaxRAMPercentage=75", "-jar", "/app/brew-focus.jar"]
