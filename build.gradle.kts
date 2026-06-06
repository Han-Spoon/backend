plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "7.0.2"
}

group = "com.hanspoon"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudAzureVersion"] = "7.1.0"

dependencyManagement {
	imports {
		mavenBom("com.azure.spring:spring-cloud-azure-dependencies:${property("springCloudAzureVersion")}")
	}
}

dependencies {
	// Web & Validation & Security
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")

	// Light Database Layer (Spring Data JPA + PostgreSQL + pgvector)
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.postgresql:postgresql")
	implementation("com.pgvector:pgvector:0.1.6")

	// DB Migration (Flyway) — Spring Boot 4는 자동설정이 spring-boot-flyway 모듈에 분리됨
	implementation("org.springframework.boot:spring-boot-flyway")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")

	// Azure Configuration (Key Vault secrets)
	implementation("com.azure.spring:spring-cloud-azure-starter-keyvault-secrets")

	// Azure Blob Storage (SAS 발급용 — 업로드 프록시 아님)
	implementation("com.azure:azure-storage-blob")

	// OpenAPI & Swagger (Spring Boot 4 compatible)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	// JWT Utilities
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// Cache & Rate Limit
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("com.bucket4j:bucket4j_jdk17-core:8.18.0")

	// Developer Tools (Lombok)
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Test - Testcontainers (통합 테스트용 PostgreSQL). Testcontainers 2.x 아티팩트 명명 사용
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Spring Boot 실행 가능 jar만 생성 (plain jar 비활성 → Docker COPY 글롭 모호성 제거)
tasks.named<Jar>("jar") {
	enabled = false
}

spotless {
	java {
		target("src/**/*.java")
		palantirJavaFormat("2.50.0")
		removeUnusedImports()
		importOrder()
		formatAnnotations()
		trimTrailingWhitespace()
		endWithNewline()
	}
}
