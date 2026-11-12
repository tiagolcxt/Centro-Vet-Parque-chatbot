package com.clinicavet.assistant.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * MySQL real via Testcontainers, mesma versão do compose.yml (ADR-0006). O contêiner é um bean do
 * contexto de teste; como todos os testes de integração usam a mesma configuração, o Spring
 * reaproveita o contexto e o contêiner é compartilhado na execução.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:9.6")
                .withEnv("TZ", "UTC")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
    }
}
