# ============================================================
# Dockerfile — CRM Backend (Spring Boot 4 / Java 21)
# Pune acest fisier in: CRM_Backend/Dockerfile
# ============================================================

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S crm && adduser -S crm -G crm
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir -p /app/uploads && chown -R crm:crm /app
USER crm
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
