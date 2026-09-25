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

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val specAcceptanceTag = "spec-acceptance"

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
            }
        }

        // Integration tests (*IT) — Testcontainers / @SpringBootTest (rules/testing.md §2).
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(junitJupiterVersion)
            dependencies {
                implementation(project())
                implementation(libs.spring.boot.starter.test)
            }
            targets {
                all {
                    testTask.configure {
                        useJUnitPlatform {
                            excludeTags(specAcceptanceTag)
                        }
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

val integrationTest = tasks.named<Test>("integrationTest")

// Like the built-in test suite, integration tests see the application's own dependencies (e.g. Spring Web types).
configurations.named("integrationTestImplementation") { extendsFrom(configurations.implementation.get()) }
configurations.named("integrationTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }

// Black-box API tests derived from spec/api-contract.md, spec/state-machine.md and spec/test-strategy.md, written
// ahead of the implementation. They live in the integrationTest source set (their final home) but are tagged and run
// separately until the endpoints exist, so they don't turn `check` red. Remove the tag from a class once the feature
// it covers is implemented (implementation plan STEP-40/42).
tasks.register<Test>("specAcceptanceTest") {
    description = "Runs spec-derived API acceptance tests written ahead of the implementation."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        includeTags(specAcceptanceTag)
    }
    shouldRunAfter(integrationTest)
}

// Coverage report aggregates execution data from both suites (spec/test-strategy.md §14).
tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport)
}
