package com.clinicavet.assistant.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** Relógio controlável e modelo falso, no lugar dos reais, para todos os testes {@link IntegrationTest}. */
@TestConfiguration(proxyBeanMethods = false)
public class TestDoublesConfiguration {

    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock(Instant.parse("2026-11-16T12:00:00Z"));
    }

    @Bean
    @Primary
    FakeAssistantModel fakeAssistantModel() {
        return new FakeAssistantModel();
    }
}
