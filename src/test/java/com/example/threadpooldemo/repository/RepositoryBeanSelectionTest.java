package com.example.threadpooldemo.repository;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.*;

import com.example.threadpooldemo.ThreadpoolExecutorSpringbootDemoApplication;

public class RepositoryBeanSelectionTest {

    @Test
    public void whenPersistenceEnabled_thenPersistentRepositoryBeanPresent() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ThreadpoolExecutorSpringbootDemoApplication.class)
                .properties("spring.main.web-application-type=none", "app.persistence.enabled=true", "spring.datasource.url=jdbc:h2:mem:beanselect1")
                .run();
        try {
            // Instead of relying on the TaskRepositoryPort concrete class (proxy names vary),
            // assert that JPA components are available when persistence is enabled.
            String[] names = ctx.getBeanNamesForType(com.example.threadpooldemo.repository.JpaTaskRepository.class);
            assertTrue(names.length > 0, "Expected JpaTaskRepository bean to be present when persistence enabled");
        } finally {
            ctx.close();
        }
    }

    @Test
    public void whenPersistenceDisabled_thenInMemoryRepositoryBeanPresent() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ThreadpoolExecutorSpringbootDemoApplication.class)
                .properties("spring.main.web-application-type=none", "app.persistence.enabled=false")
                .run();
        try {
            String[] names = ctx.getBeanNamesForType(com.example.threadpooldemo.repository.TaskRepository.class);
            assertTrue(names.length > 0, "Expected in-memory TaskRepository bean to be present when persistence disabled");
        } finally {
            ctx.close();
        }
    }
}
