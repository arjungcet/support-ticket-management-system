plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.supportdesk"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Security override of a Spring Boot managed version; see the comment on `tomcat` in gradle/libs.versions.toml.
extra["tomcat.version"] = libs.versions.tomcat.get()

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    // Lightweight local runs (`h2` profile) and, while Docker is unavailable, the integration tests (see below).
    runtimeOnly(libs.h2)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// JUnit version comes from the Spring Boot BOM so test suites never pin a different Jupiter version.
val junitJupiterVersion: String = dependencyManagement.importedProperties["junit-jupiter.version"]
    ?: error("junit-jupiter.version not found in Spring Boot BOM")

testing {
    suites {
        // Unit and slice tests (*Test) — no Docker required (rules/testing.md §2).
        named<JvmTestSuite>("test") {
            useJUnitJupiter(junitJupiterVersion)
            dependencies {
                implementation(libs.spring.boot.starter.test)
                implementation(libs.archunit.junit5)
            }
        }

        // Integration tests (*IT) — Testcontainers / @SpringBootTest (rules/testing.md §2).
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(junitJupiterVersion)
            dependencies {
                implementation(project())
                implementation(libs.spring.boot.starter.test)
                implementation(libs.spring.boot.testcontainers)
                implementation(libs.testcontainers.postgresql)
            }
            targets {
                all {
                    testTask.configure {
                        // Database behaviour is tested on PostgreSQL via Testcontainers (rules/testing.md §4), so
                        // Docker is required. Without it these tests fail fast — they never fall back to H2.
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

val integrationTest = tasks.named<Test>("integrationTest")

// Mockito's inline mock maker as a -javaagent instead of self-attaching at runtime, which recent JDKs warn about and
// will block by default (JEP 451). Version from the Spring Boot BOM.
val mockitoAgent: Configuration = configurations.create("mockitoAgent")
dependencies {
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}
tasks.test {
    // -Xshare:off silences the JVM's class-data-sharing notice that any boot-classpath agent triggers.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}", "-Xshare:off") })
}

// Like the built-in test suite, integration tests see the application's own dependencies (e.g. Spring Web types).
configurations.named("integrationTestImplementation") { extendsFrom(configurations.implementation.get()) }
configurations.named("integrationTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }

// Coverage report aggregates execution data from both suites (spec/test-strategy.md §14).
tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required = true
        html.required = true
    }
}

// Coverage floor on the business code (rules/testing.md §3, plan STEP-45), over both test suites.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.supportdesk.ticket.domain", "com.supportdesk.ticket.application")
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}
