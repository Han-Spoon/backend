# syntax=docker/dockerfile:1

# native로 jar 빌드 → arm64 런타임 이미지에 COPY

FROM eclipse-temurin:21-jre

# install curl (ECS healthCheck)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
