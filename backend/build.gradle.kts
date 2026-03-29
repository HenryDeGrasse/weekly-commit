plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.weeklycommit"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("me.paulschwarz:spring-dotenv:4.0.0")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        // Exclude eval and ab-eval tests from normal test runs — they call real LLM APIs
        excludeTags("eval", "ab-eval")
    }
}

// Dedicated task for running eval tests against real LLM
tasks.register<Test>("evalTest") {
    description = "Run AI prompt evaluation tests against real LLM (requires OPENROUTER_API_KEY)"
    group = "verification"

    // Use same classpath and sources as the standard test task
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("eval")
    }
    // Eval tests need longer timeouts (each case calls the LLM)
    systemProperty("junit.jupiter.execution.timeout.default", "120s")
    // Forward env vars
    environment("OPENROUTER_API_KEY", System.getenv("OPENROUTER_API_KEY") ?: "")
    environment("OPENROUTER_MODEL", System.getenv("OPENROUTER_MODEL") ?: "anthropic/claude-sonnet-4-20250514")
}

// Dedicated task for running offline A/B model comparison evaluation
tasks.register<Test>("abEvalTest") {
    description = "Run offline A/B model comparison eval (requires OPENROUTER_API_KEY and AB_* env vars)"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("ab-eval")
    }
    // A/B eval calls two models per case — allow generous timeouts
    systemProperty("junit.jupiter.execution.timeout.default", "300s")
    // Forward API key and all AB_* configuration env vars
    environment("OPENROUTER_API_KEY", System.getenv("OPENROUTER_API_KEY") ?: "")
    environment("AB_CONTROL_MODEL", System.getenv("AB_CONTROL_MODEL") ?: "")
    environment("AB_TREATMENT_MODEL", System.getenv("AB_TREATMENT_MODEL") ?: "")
    environment("AB_CONTROL_EMBEDDING", System.getenv("AB_CONTROL_EMBEDDING") ?: "")
    environment("AB_TREATMENT_EMBEDDING", System.getenv("AB_TREATMENT_EMBEDDING") ?: "")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Set working directory to monorepo root so spring-dotenv picks up .env
    workingDir = rootProject.projectDir.parentFile
}

spotless {
    java {
        // google-java-format/palantirJavaFormat use JDK-internal APIs removed in JDK 25.
        // Eclipse JDT formatter is fully JDK-agnostic and enforces consistent style.
        eclipse()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
