package com.supportdesk.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Marks a black-box API test derived from the specifications (written ahead of the implementation). Runs the full
 * application on a random port, against PostgreSQL in a Testcontainer, as part of {@code ./gradlew integrationTest} /
 * {@code build}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
public @interface SpecAcceptanceTest {
}
