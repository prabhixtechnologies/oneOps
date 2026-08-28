# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 — build the Spring Boot fat JAR with a cacheable dependency layer.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so code-only changes reuse this layer.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Stage 2 — minimal JRE runtime, non-root, container-aware JVM flags.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is only used by HEALTHCHECK; kept out of the build stage.
RUN apk add --no-cache curl \
    && addgroup -g 1001 prabhix \
    && adduser -u 1001 -G prabhix -s /bin/sh -D prabhix

WORKDIR /app

COPY --from=build --chown=prabhix:prabhix /build/target/prabhix-platform.jar ./app.jar

USER prabhix

EXPOSE 8080

# Let the JVM size itself from cgroup memory limits instead of a fixed -Xmx.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
