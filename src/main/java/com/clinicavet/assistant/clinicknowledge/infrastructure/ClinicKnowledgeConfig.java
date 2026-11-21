package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.shared.exception.InvalidDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A base institucional que o assistente recebe por injeção (ADR-0026): a cada leitura, a versão publicada da clínica
 * atual. Quem a usa não sabe de clínicas nem de versões.
 * <p>
 * <b>Carga inicial por arquivo</b> (RF-S19): se {@code assistant.knowledge.location} apontar para um YAML e a clínica
 * de {@code assistant.knowledge.bootstrap-clinic} ainda não tiver versão publicada, o arquivo é importado e publicado
 * na inicialização. Serve para a clínica fictícia de desenvolvimento e testes. Depois disso a configuração é feita
 * pela web, e o arquivo é ignorado. Arquivo ausente ou inválido não impede a aplicação de subir (R-15): o problema
 * vai para o log e a clínica fica sem base publicada.
 */
@Configuration
class ClinicKnowledgeConfig {

    private static final Logger log = LoggerFactory.getLogger(ClinicKnowledgeConfig.class);

    @Bean
    ClinicKnowledge clinicKnowledge(PublishedKnowledge published) {
        return ClinicKnowledge.dynamic(published::current);
    }

    @Bean
    @Order(10)
    ApplicationRunner knowledgeBootstrap(ClinicConfig config, ResourceLoader resources,
                                         @Value("${assistant.knowledge.location:}") String location,
                                         @Value("${assistant.knowledge.bootstrap-clinic:00000000-0000-4000-8000-000000000001}")
                                         String clinic) {
        return (ApplicationArguments args) -> bootstrap(config, resources, location, ClinicId.of(clinic));
    }

    static void bootstrap(ClinicConfig config, ResourceLoader resources, String location, ClinicId clinic) {
        if (location == null || location.isBlank()) {
            return;
        }
        CurrentClinic.run(clinic, () -> {
            if (config.published().isPresent()) {
                log.info("Configuração da clínica já publicada; arquivo de carga inicial ignorado [arquivo={}]", location);
                return;
            }
            Resource resource = resources.getResource(location);
            if (!resource.exists()) {
                log.warn("Arquivo de carga inicial não encontrado [arquivo={}]", location);
                return;
            }
            try {
                String yaml = resource.getContentAsString(StandardCharsets.UTF_8);
                config.importYaml(yaml);
                ClinicConfig.Version version = config.publish("Carga inicial do arquivo " + resource.getFilename());
                log.info("Configuração importada do arquivo e publicada [arquivo={}, versão={}]", location,
                        version.summary().number());
            } catch (InvalidDocumentException e) {
                log.error("Arquivo de carga inicial inválido; nada publicado [arquivo={}]:\n  - {}", location,
                        String.join("\n  - ", e.problems()));
            } catch (IOException e) {
                log.error("Arquivo de carga inicial ilegível [arquivo={}]", location);
            }
        });
    }
}
