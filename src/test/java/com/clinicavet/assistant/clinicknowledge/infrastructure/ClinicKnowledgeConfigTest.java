package com.clinicavet.assistant.clinicknowledge.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A aplicação não sobe sem base ou com base inválida (ADR-0003, fail-fast). */
class ClinicKnowledgeConfigTest {

    private final ClinicKnowledgeConfig config = new ClinicKnowledgeConfig();
    private final DefaultResourceLoader loader = new DefaultResourceLoader();

    @Test
    void missingFile_stopsStartup_withHowToFix() {
        assertThatThrownBy(() -> config.clinicKnowledge(loader, "file:config/nao-existe.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("config/nao-existe.yml")
                .hasMessageContaining("ASSISTANT_KNOWLEDGE_FILE");
    }

    @Test
    void versionedFiles_load() {
        assertThat(config.clinicKnowledge(loader, "file:config/clinic-knowledge.example.yml").promptItems()).isNotEmpty();
        assertThat(config.clinicKnowledge(loader, "file:config/clinic-knowledge.ficticia.yml").pricesDisclosed()).isTrue();
    }
}
