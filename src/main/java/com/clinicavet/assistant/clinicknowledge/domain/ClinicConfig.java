package com.clinicavet.assistant.clinicknowledge.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Configuração da clínica atual, versionada (ADR-0026, S3-1): no máximo um rascunho e uma publicada; publicar arquiva a
 * anterior na mesma transação. O documento tem a estrutura do modelo de informações (schema v2): todas as chaves
 * presentes, {@code null} = desconhecido.
 * <p>
 * Salvar e publicar validam o documento inteiro. Documento inválido gera {@code InvalidDocumentException} com todos os
 * problemas, no formato {@code secao.campo: motivo}, e nada é gravado.
 */
public interface ClinicConfig {

    enum Status {
        DRAFT, PUBLISHED, ARCHIVED
    }

    record VersionSummary(UUID id, int number, Status status, String note, Instant createdAt, UUID createdBy,
                          Instant updatedAt, Instant publishedAt, UUID publishedBy, long revision) {
    }

    record Version(VersionSummary summary, Map<String, Object> document) {
    }

    /** Uma diferença legível entre duas versões (S3-6): {@code null} em um dos lados = item novo ou removido. */
    record Change(String key, String before, String after) {
    }

    /** Situação de cada seção com validade (S3-9): para os avisos da tela Início e da configuração. */
    record SectionValidity(String section, LocalDate validUntil, boolean expired, boolean expiringSoon) {
    }

    record Overview(VersionSummary published, VersionSummary draft, List<SectionValidity> validity,
                    boolean draftDiffers) {
    }

    Overview overview();

    Optional<Version> published();

    /** O rascunho atual; se não houver, cria um a partir da publicada (ou do documento vazio). */
    Version draft();

    /**
     * Substitui o documento do rascunho.
     *
     * @param revision a revisão que o cliente leu ({@link VersionSummary#revision()}); outra = alguém salvou antes
     */
    Version saveDraft(Map<String, Object> document, long revision);

    Version publish(String note);

    void discardDraft();

    List<VersionSummary> history();

    Version version(UUID id);

    /** Copia uma versão antiga para o rascunho (substitui o rascunho atual). */
    Version restore(UUID id);

    /** Diferenças de {@code from} para {@code to}. */
    List<Change> diff(UUID from, UUID to);

    /** Diferenças da publicada para o rascunho (tudo, se não houver publicada). */
    List<Change> draftChanges();

    /** Importa o YAML do modelo de informações como rascunho (RF-S19). */
    Version importYaml(String yaml);

    /** O rascunho lido como base, para o simulador (ADR-0026 item 7). */
    ClinicKnowledge draftKnowledge();

    /** Documento com todas as chaves e tudo desconhecido. */
    Map<String, Object> blankDocument();

    /** Publica um documento direto, sem passar pela tela (carga inicial da clínica fictícia e modelos). */
    Version publishDocument(Map<String, Object> document, String note);
}
