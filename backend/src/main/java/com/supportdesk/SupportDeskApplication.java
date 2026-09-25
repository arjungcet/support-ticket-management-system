package com.supportdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Support Ticket Management System backend.
 *
 * <p>Lives in the root package {@code com.supportdesk} so component scanning covers every feature package
 * ({@code ticket}, {@code shared}) described in {@code spec/architecture.md} §5.1.
 */
@SpringBootApplication
public class SupportDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportDeskApplication.class, args);
    }
}
