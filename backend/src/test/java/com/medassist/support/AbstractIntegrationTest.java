package com.medassist.support;

import com.medassist.MedAssistApplication;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for API/integration tests: full application context, real PostgreSQL (pgvector) via
 * Testcontainers, Flyway migrations, MockMvc with JWT-authenticated synthetic users.
 */
@SpringBootTest(classes = MedAssistApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainer.class, ScriptedAiConfiguration.class})
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }
}
