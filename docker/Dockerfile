# ---- Build stage ----
# Full JDK + Maven. Discarded after the JAR is built.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependencies in a separate layer.
# Re-running `mvn package` after a source-only change reuses this layer
# and skips the dependency download entirely.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the JAR.
COPY src ./src
RUN mvn package -DskipTests


# ---- Runtime stage ----
# Slim JRE on Alpine. This is the only stage that ships.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root for security.
RUN addgroup -S app && adduser -S app -G app
USER app

# Copy ONLY the built JAR from the builder stage.
# JDK, Maven, source, and build caches stay behind.
COPY --from=builder /build/target/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
