package com.supportdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for security review H-1: Tomcat 11.0.24 is affected by GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6
 * and GHSA-h3x4-894j-xpx5 (all fixed in 11.0.25). Fails if a dependency change brings a vulnerable Tomcat back.
 */
class EmbeddedTomcatVersionTest {

    private static final int[] FIRST_FIXED = {11, 0, 25};

    @Test
    @DisplayName("embedded Tomcat is at least 11.0.25 (security review H-1)")
    void embeddedTomcat_isNotAffectedByKnownCriticalAdvisories() {
        String serverNumber = ServerInfo.getServerNumber();

        int[] version = Arrays.stream(serverNumber.split("\\.")).limit(3).mapToInt(Integer::parseInt).toArray();

        assertThat(Arrays.compare(version, FIRST_FIXED))
                .as("Tomcat %s must be >= 11.0.25", serverNumber)
                .isGreaterThanOrEqualTo(0);
    }
}
