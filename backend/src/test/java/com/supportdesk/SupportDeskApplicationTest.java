package com.supportdesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class SupportDeskApplicationTest {

    @Test
    @DisplayName("application class is a @SpringBootApplication in the root package so all features are scanned")
    void applicationClass_isSpringBootApplicationInRootPackage() {
        // Given
        Class<SupportDeskApplication> applicationClass = SupportDeskApplication.class;

        // When
        boolean annotated = applicationClass.isAnnotationPresent(SpringBootApplication.class);

        // Then
        assertThat(annotated).isTrue();
        assertThat(applicationClass.getPackageName()).isEqualTo("com.supportdesk");
    }
}
