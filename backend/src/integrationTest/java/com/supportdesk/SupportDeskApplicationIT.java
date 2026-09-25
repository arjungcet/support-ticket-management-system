package com.supportdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.PostgresTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class SupportDeskApplicationIT {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("application context starts")
    void contextLoads() {
        assertThat(context.getBean(SupportDeskApplication.class)).isNotNull();
    }
}
