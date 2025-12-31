/*
 * PressO Desktop - Orchestration Kernel
 * ======================================
 * 
 * ARCHITECTURAL CONTEXT:
 * This is the central Java Orchestration Kernel for PressO Desktop.
 * It coordinates all engines (Python, Rust, Go) via subprocess communication.
 * 
 * FORBIDDEN (per PROJECT_DOCUMENTATION.md):
 * - NO HTTP/REST/Web Server dependencies
 * - NO Spring Boot, Quarkus, Micronaut
 * - NO external network call libraries
 * 
 * ALLOWED:
 * - Standard Java library only (Phase 1)
 * - SQLite JDBC driver (for database access)
 * - JSON processing library (for IPC messages)
 * - Logging facade (SLF4J with simple backend)
 */

plugins {
    java
    application
}

group = "com.presso"
version = "0.1.0"

java {
    // Java 21 LTS - see gradle.properties for justification
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JSON processing for IPC messages (JSON-RPC 2.0 style)
    // Minimal dependency - no web framework
    implementation("com.google.code.gson:gson:2.10.1")
    
    // SQLite JDBC - Kernel owns database access
    // Reference: PROJECT_DOCUMENTATION.md Section 5.1
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // Logging - minimal structured logging
    // Reference: User task requirement
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    // Testing (Phase 1 - structure only)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    // Entry point for the Kernel
    mainClass.set("com.presso.kernel.KernelMain")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.presso.kernel.KernelMain",
            "Implementation-Title" to "PressO Orchestration Kernel",
            "Implementation-Version" to project.version
        )
    }
}

/*
 * TODO (Phase 2+):
 * - Add shadow/fat JAR plugin for single-file deployment
 * - Configure native image generation (GraalVM) for faster startup
 * - Add integration tests for engine subprocess communication
 */

