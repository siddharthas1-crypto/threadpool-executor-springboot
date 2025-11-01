package com.example.threadpooldemo.repository;

import com.example.threadpooldemo.ThreadpoolExecutorSpringbootDemoApplication;
import com.example.threadpooldemo.dto.TaskStatusDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PersistentTaskRepositoryIntegrationTest {

    @AfterAll
    public static void cleanup() {
        // Remove H2 files produced by the test
        new File("target/testdb.mv.db").delete();
        new File("target/testdb.trace.db").delete();
    }

    @Test
    public void testPersistenceAcrossContextRestarts() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.main.web-application-type", "none");
        props.put("app.persistence.enabled", "true");
    String base = System.getProperty("user.dir");
    String fileDb = String.format("%s/target/testdb", base.replace('\\', '/'));
    props.put("spring.datasource.url", "jdbc:h2:file:" + fileDb + ";DB_CLOSE_DELAY=-1;LOCK_MODE=0");
        props.put("spring.datasource.driver-class-name", "org.h2.Driver");
        props.put("spring.datasource.username", "sa");
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.hibernate.ddl-auto", "update");

        // Start first context and save an entity
        ConfigurableApplicationContext ctx1 = new SpringApplicationBuilder(ThreadpoolExecutorSpringbootDemoApplication.class)
                .properties(props)
                .run();

        try {
            // Use the JPA repository directly to ensure persistence to the H2 file DB
            JpaTaskRepository jpa = ctx1.getBean(JpaTaskRepository.class);
            TaskEntity entity = new TaskEntity("p1", "fileA.jpg", "QUEUED", null);
            jpa.saveAndFlush(entity);
            // verify immediate visibility within the same context
            org.junit.jupiter.api.Assertions.assertTrue(jpa.existsById("p1"));
        } finally {
            ctx1.close();
        }

        // Start second context with the same DB and assert persisted row exists
        ConfigurableApplicationContext ctx2 = new SpringApplicationBuilder(ThreadpoolExecutorSpringbootDemoApplication.class)
                .properties(props)
                .run();

        try {
            JpaTaskRepository jpa2 = ctx2.getBean(JpaTaskRepository.class);
            org.junit.jupiter.api.Assertions.assertTrue(jpa2.existsById("p1"), "Persisted task should be available after context restart");
            TaskEntity reloaded = jpa2.findById("p1").orElse(null);
            Assertions.assertNotNull(reloaded, "Entity should be present in second context");
            Assertions.assertEquals("QUEUED", reloaded.getStatus());
        } finally {
            ctx2.close();
        }
    }
}
