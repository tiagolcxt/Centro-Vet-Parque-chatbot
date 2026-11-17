package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.nio.charset.StandardCharsets;

/**
 * Carrega a base institucional na inicialização (ADR-0003). Local em {@code assistant.knowledge.location}
 * ({@code ASSISTANT_KNOWLEDGE_FILE}). Arquivo ausente ou inválido: a aplicação não sobe.
 */
@Configuration
class ClinicKnowledgeConfig {

    private static final Logger log = LoggerFactory.getLogger(ClinicKnowledgeConfig.class);

    @Bean
    ClinicKnowledge clinicKnowledge(ResourceLoader resources, Clock clock,
                                    @Value("${assistant.knowledge.location}") String location) {
        Resource resource = resources.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Base institucional não encontrada: " + location
                    + ". Defina ASSISTANT_KNOWLEDGE_FILE (veja config/clinic-knowledge.example.yml).");
        }
        String content;
        try {
            content = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Base institucional ilegível: " + location, e);
        }
        ClinicKnowledge knowledge = KnowledgeFileLoader.parse(content, location, clock);
        long known = knowledge.promptItems().stream().filter(i -> i.isKnown()).count();
        log.info("Base institucional carregada [arquivo={}, itens={}, conhecidos={}, preçosDivulgados={}]",
                location, knowledge.promptItems().size(), known, knowledge.pricesDisclosed());
        if (!knowledge.expiredSections().isEmpty()) {
            log.warn("Base institucional com seções VENCIDAS, tratadas como desconhecidas até a revisão "
                    + "[seções={}]", knowledge.expiredSections());
        }
        if (!knowledge.sectionsWithoutValidity().isEmpty()) {
            log.warn("Base institucional com seções sem 'válido até' (review.valid-until) [seções={}]",
                    knowledge.sectionsWithoutValidity());
        }
        return knowledge;
    }
}
