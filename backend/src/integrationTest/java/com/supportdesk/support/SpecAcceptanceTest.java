package com.supportdesk.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Marks a black-box API test derived from the specifications and written ahead of the implementation.
 *
 * <p>Tagged {@code spec-acceptance}: excluded from {@code integrationTest}/{@code check} and run with
 * {@code ./gradlew specAcceptanceTest} until the feature exists (see {@code build.gradle.kts}). Once a feature is
 * implemented, replace this annotation with a plain {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} so the class
 * becomes part of the regular integration suite.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("spec-acceptance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface SpecAcceptanceTest {
}
