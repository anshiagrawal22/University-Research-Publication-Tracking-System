# Stage 1: Build Spring Boot fat JAR
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Minimal non-root multi-arch Java 17 runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser \
    && mkdir -p /app/data \
    && chown -R appuser:appgroup /app

COPY --from=builder /app/target/publication-portal.jar /app/app.jar
RUN chown appuser:appgroup /app/app.jar

USER appuser

ENV DB_URL="jdbc:h2:file:/app/data/publications;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE;WRITE_DELAY=0"
# The app defaults to 8081 for local dev (8080 is often taken); inside the container keep 8080.
ENV SERVER_PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/api/dashboard/summary >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
