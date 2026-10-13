package com.medassist.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL + pgvector container for integration tests (same image as docker-compose). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    static final String IMAGE = System.getProperty("medassist.test.postgres-image", "pgvector/pgvector:pg17");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("medassist_test")
                .withReuse(false);
    }
}
