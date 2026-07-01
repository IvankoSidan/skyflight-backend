plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "com.wheezy"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.clojars.org/") }
	maven { url = uri("https://maven.google.com") }
}

dependencies {
	// ============================================================
	// CORE SPRING
	// ============================================================
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// ============================================================
	// OKHTTP для Gmail API
	// ============================================================
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

	// ============================================================
	// KOTLIN
	// ============================================================
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// ============================================================
	// PDF GENERATION
	// ============================================================
	implementation("org.xhtmlrenderer:flying-saucer-pdf-openpdf:9.1.22")
	implementation("org.jsoup:jsoup:1.17.2")

	// ============================================================
	// JWT
	// ============================================================
	implementation("io.jsonwebtoken:jjwt-api:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

	// ============================================================
	// WEBSOCKET + FIREBASE
	// ============================================================
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("com.google.firebase:firebase-admin:9.2.0")
	implementation("org.springframework.boot:spring-boot-starter-reactor-netty")

	// ============================================================
	// STRIPE
	// ============================================================
	implementation("com.stripe:stripe-java:29.2.0")

	// ============================================================
	// VALIDATION
	// ============================================================
	implementation("jakarta.validation:jakarta.validation-api:3.0.2")
	implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

	// ============================================================
	// DATABASE
	// ============================================================
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.apache.commons:commons-pool2")

	// ============================================================
	// EMAIL (GMAIL API + SMTP fallback)
	// ============================================================
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("jakarta.mail:jakarta.mail-api:2.1.2")
	implementation("com.sun.mail:jakarta.mail:2.0.1")

	// ============================================================
	// DEVELOPMENT
	// ============================================================
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// ============================================================
	// MONITORING
	// ============================================================
	implementation("io.micrometer:micrometer-registry-prometheus")

	// ============================================================
	// SWAGGER / OPENAPI
	// ============================================================
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
	implementation("org.springdoc:springdoc-openapi-starter-common:2.5.0")

	// ============================================================
	// TESTS
	// ============================================================
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.mockito:mockito-core:4.+")
	testImplementation("org.mockito.kotlin:mockito-kotlin:4.+")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}