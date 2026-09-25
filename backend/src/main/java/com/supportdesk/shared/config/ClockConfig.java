package com.supportdesk.shared.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /**
     * UTC clock truncated to microseconds, the precision of {@code timestamptz}, so values read back from the
     * database equal the values written (spec/data-model.md §11).
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
