# Docker Multi-Stage Build Example

Comparison of a naive single-stage Dockerfile against a multi-stage one,
using a minimal Spring Boot app.

## Files

- `Dockerfile.single` — single-stage build (bundles JDK, Maven, source, JAR)
- `Dockerfile` — multi-stage build (builder + slim JRE runtime)

## `Dockerfile.single`

```dockerfile
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/app.jar"]
```

Everything lands in the final image: JDK, Maven, the local `~/.m2` cache,
source code, and the JAR. Result: ~900 MB.

## `Dockerfile` (multi-stage)

```dockerfile
# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/target/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Key differences from the single-stage version:

1. **Two `FROM` statements.** The first stage is named `builder` via `AS builder`.
   Only the final `FROM` produces the shipped image.
2. **Dependency layer is separate.** `COPY pom.xml` + `mvn dependency:go-offline`
   runs before `COPY src`, so source changes don't bust the dependency cache.
3. **Runtime base is `jre-alpine`, not `maven`.** No JDK, no Maven, no shell tools.
4. **Non-root user.** `addgroup` + `adduser` + `USER app`.
5. **`COPY --from=builder`** pulls only the built JAR across — everything else
   in the builder stage is discarded.

## Build and Compare

```bash
docker build -f Dockerfile.single -t demo:single .
docker build -f Dockerfile        -t demo:multi  .

docker images | grep demo
```

Expected output:

```
REPOSITORY   TAG       SIZE
demo         multi     ~210 MB
demo         single    ~900 MB
```

Roughly a **77% reduction** in image size, plus a much smaller attack surface
(no build tools, no source code, non-root user).
