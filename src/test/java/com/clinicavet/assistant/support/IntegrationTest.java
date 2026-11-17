package com.clinicavet.assistant.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Teste de integração com MySQL real (Testcontainers) e servidor HTTP real em porta aleatória. Exige
 * Docker em execução. Usa o {@link FakeAssistantModel} e o {@link TestClock}: nunca chama a OpenAI nem
 * usa dados reais (DA-17). Prazos de expiração de teste explícitos (P-05 ainda pendente) e base
 * institucional fictícia ({@code config/clinic-knowledge.ficticia.yml}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.security.api-key=" + IntegrationTest.API_KEY,
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "assistant.conversation.retry-backoff=0s",
        "assistant.knowledge.location=file:config/clinic-knowledge.ficticia.yml"
})
@Import({TestcontainersConfiguration.class, TestDoublesConfiguration.class})
public @interface IntegrationTest {

    /** Chave fictícia, só de teste. */
    String API_KEY = "chave-de-teste-ficticia-0123456789abcdef";
}
