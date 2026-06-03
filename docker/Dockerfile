# syntax=docker/dockerfile:1
# 멀티스테이지: 소스로부터 bootJar 빌드 → JRE 런타임 이미지
# 빌드 컨텍스트는 레포 루트. (az acr build / docker build -f docker/Dockerfile .)

# 1) Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 캐시 레이어 (빌드 스크립트/래퍼 먼저 복사)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

# 소스 복사 후 실행 가능한 jar 생성 (테스트/스폿리스는 CI에서 수행 → 이미지 빌드는 스킵)
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test -x spotlessCheck

# 2) Runtime stage
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# bootJar 산출물 (plain jar는 build.gradle.kts에서 비활성)
COPY --from=builder /workspace/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
