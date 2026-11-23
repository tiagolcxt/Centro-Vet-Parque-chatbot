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
        "assistant.security.additional-api-keys=" + IntegrationTest.OTHER_CLINIC + "=" + IntegrationTest.OTHER_CLINIC_API_KEY,
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "assistant.conversation.retry-backoff=0s",
        "assistant.knowledge.location=file:config/clinic-knowledge.ficticia.yml",
        "assistant.whatsapp.app-secret=" + IntegrationTest.WHATSAPP_APP_SECRET,
        "assistant.whatsapp.verify-token=" + IntegrationTest.WHATSAPP_VERIFY_TOKEN,
        "assistant.whatsapp.token-key=ZmljdGljaWEtY2hhdmUtZGUtdGVzdGUtMzItYnl0ZXM=",
        "assistant.whatsapp.scheduling=false",
        "assistant.alerts.scheduling=false",
        "assistant.whatsapp.inbound-async=false"
})
@Import({TestcontainersConfiguration.class, TestDoublesConfiguration.class})
public @interface IntegrationTest {

    /** Chave fictícia, só de teste. */
    String API_KEY = "chave-de-teste-ficticia-0123456789abcdef";

    /** Segunda clínica fictícia, para os testes de isolamento (ADR-0021). Criada por {@code OtherClinic.ensure}. */
    String OTHER_CLINIC = "00000000-0000-4000-8000-000000000002";

    /** App Secret e token de verificação fictícios do webhook do WhatsApp (Etapa S4). */
    String WHATSAPP_APP_SECRET = "app-secret-ficticio-de-teste";
    String WHATSAPP_VERIFY_TOKEN = "verify-token-ficticio";

    /** Chave fictícia da segunda clínica. */
    String OTHER_CLINIC_API_KEY = "chave-ficticia-da-outra-clinica-0123456789";
}
