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

// ---------------------------------------------------------------------------
// Helper: read from env first, then fall back to the root .env file.
// This ensures secrets reach the test JVM even when the Gradle daemon was
// started before the variable was exported in the calling shell.
// ---------------------------------------------------------------------------
fun envOrDotenv(key: String, default: String = ""): String {
    val fromEnv = System.getenv(key)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val dotenv = rootProject.projectDir.parentFile.resolve(".env")
    if (dotenv.exists()) {
        dotenv.bufferedReader().lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("$key=") && !it.startsWith("#") }
            .map { it.substringAfter("=").trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { return it }
    }
    return default
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        // Exclude eval and ab-eval tests from normal test runs — they call real LLM APIs
        excludeTags("eval", "ab-eval", "model-compare")
    }
}

// Dedicated task for running eval tests against real LLM
tasks.register<Test>("evalTest") {
    description = "Run AI prompt evaluation tests against real LLM (requires OPENROUTER_API_KEY)"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("eval")
    }
    systemProperty("junit.jupiter.execution.timeout.default", "120s")
    environment("OPENROUTER_API_KEY", envOrDotenv("OPENROUTER_API_KEY"))
    environment("OPENROUTER_MODEL",   envOrDotenv("OPENROUTER_MODEL", "openai/gpt-4.1-nano"))
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
    systemProperty("junit.jupiter.execution.timeout.default", "300s")
    environment("OPENROUTER_API_KEY",        envOrDotenv("OPENROUTER_API_KEY"))
    environment("AB_CONTROL_MODEL",          envOrDotenv("AB_CONTROL_MODEL"))
    environment("AB_TREATMENT_MODEL",        envOrDotenv("AB_TREATMENT_MODEL"))
    environment("AB_CONTROL_EMBEDDING",      envOrDotenv("AB_CONTROL_EMBEDDING"))
    environment("AB_TREATMENT_EMBEDDING",    envOrDotenv("AB_TREATMENT_EMBEDDING"))
}

// N-way model leaderboard: runs every eval dataset against a configurable list of models
// and ranks them by composite score (schema correctness + judge quality + latency).
//
// Usage:
//   ./gradlew modelCompareTest                                     # full run
//   QUICK=true ./gradlew modelCompareTest                          # 2-min quality check
//   COMPARE_MODELS="openai/gpt-4o,openai/gpt-4.1-nano" ./gradlew modelCompareTest
tasks.register<Test>("modelCompareTest") {
    description = "Run N-way model leaderboard eval (requires OPENROUTER_API_KEY)"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("model-compare")
    }
    // Each case calls N models concurrently + optional Opus judge — allow generous timeout
    systemProperty("junit.jupiter.execution.timeout.default", "1800s")
    environment("OPENROUTER_API_KEY",  envOrDotenv("OPENROUTER_API_KEY"))
    // Comma-separated model IDs; falls back to MultiModelEvalRunner.DEFAULT_MODELS
    environment("COMPARE_MODELS",      envOrDotenv("COMPARE_MODELS"))
    // Max eval cases per dataset to run through Opus judge (default 4)
    environment("JUDGE_SAMPLE_SIZE",   envOrDotenv("JUDGE_SAMPLE_SIZE", "4"))
    // Per-model call hard timeout in seconds (default 20)
    environment("CALL_TIMEOUT_SEC",    envOrDotenv("CALL_TIMEOUT_SEC", "20"))
    // Set QUICK=true to run only the two judge-scored datasets (~2 min)
    environment("QUICK",               envOrDotenv("QUICK", "false"))
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
        toggleOffOn()
    }
}
