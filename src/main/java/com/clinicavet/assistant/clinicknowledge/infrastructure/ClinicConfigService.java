package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rascunho, publicação e histórico da configuração da clínica atual (ADR-0026). Publicar arquiva a anterior na mesma
 * transação e, depois do commit, troca a base que o assistente lê ({@link PublishedKnowledge}).
 */
@Service
class ClinicConfigService implements ClinicConfig {

    /** A tela Início avisa quando falta menos que isto para uma seção vencer (S3-9). */
    static final int EXPIRING_SOON_DAYS = 15;
    static final int MAX_NOTE = 200;

    private final ConfigVersionJpaRepository versions;
    private final PublishedKnowledge published;
    private final AuditLog audit;
    private final Clock clock;

    ClinicConfigService(ConfigVersionJpaRepository versions, PublishedKnowledge published, AuditLog audit,
                        Clock clock) {
        this.versions = versions;
        this.published = published;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Overview overview() {
        Optional<ConfigVersionEntity> live = versions.findByStatus(Status.PUBLISHED);
        Optional<ConfigVersionEntity> draft = versions.findByStatus(Status.DRAFT);
        List<SectionValidity> validity = new ArrayList<>();
        if (live.isPresent()) {
            ClinicKnowledge knowledge = KnowledgeDocument.read(KnowledgeDocument.fromJson(live.get().document()), clock);
            LocalDate today = LocalDate.now(clock);
            for (String section : ClinicKnowledge.VALIDATED_SECTIONS) {
                LocalDate until = knowledge.validity().get(section);
                boolean expired = until != null && today.isAfter(until);
                boolean soon = until != null && !expired && !today.plusDays(EXPIRING_SOON_DAYS).isBefore(until);
                validity.add(new SectionValidity(section, until, expired, soon));
            }
        }
        boolean differs = draft.isPresent() && (live.isEmpty()
                || !KnowledgeDocument.diff(KnowledgeDocument.fromJson(live.get().document()),
                KnowledgeDocument.fromJson(draft.get().document())).isEmpty());
        return new Overview(live.map(ConfigVersionEntity::summary).orElse(null),
                draft.map(ConfigVersionEntity::summary).orElse(null), validity, differs);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Version> published() {
        return versions.findByStatus(Status.PUBLISHED).map(ClinicConfigService::view);
    }

    @Override
    @Transactional
    public Version draft() {
        return view(currentDraft());
    }

    @Override
    @Transactional
    public Version saveDraft(Map<String, Object> document, long revision) {
        KnowledgeDocument.read(document, clock);
        ConfigVersionEntity draft = currentDraft();
        if (draft.revision() != revision) {
            throw new ConflictException("O rascunho foi alterado por outra pessoa. Recarregue antes de salvar.");
        }
        draft.replaceDocument(KnowledgeDocument.toJson(document), clock.instant());
        return view(versions.saveAndFlush(draft));
    }

    @Override
    @Transactional
    public Version publish(String note) {
        ConfigVersionEntity draft = versions.findByStatus(Status.DRAFT)
                .orElseThrow(() -> new ConflictException("Não há rascunho para publicar."));
        return publishDraft(draft, cleanNote(note));
    }

    @Override
    @Transactional
    public Version publishDocument(Map<String, Object> document, String note) {
        KnowledgeDocument.read(document, clock);
        ConfigVersionEntity draft = currentDraft();
        draft.replaceDocument(KnowledgeDocument.toJson(document), clock.instant());
        return publishDraft(versions.saveAndFlush(draft), cleanNote(note));
    }

    private Version publishDraft(ConfigVersionEntity draft, String note) {
        // Valida de novo: o rascunho pode ter sido gravado por uma versão anterior das regras.
        Map<String, Object> document = KnowledgeDocument.fromJson(draft.document());
        KnowledgeDocument.read(document, clock);
        Instant now = clock.instant();
        Optional<ConfigVersionEntity> previous = versions.findByStatus(Status.PUBLISHED);
        List<Change> changes = KnowledgeDocument.diff(
                previous.map(p -> KnowledgeDocument.fromJson(p.document())).orElse(null), document);
        previous.ifPresent(p -> {
            p.archive(now);
            versions.saveAndFlush(p);
        });
        draft.publish(note, author(), now);
        ConfigVersionEntity saved = versions.saveAndFlush(draft);
        audit.record("config.published", "config_version", saved.id(),
                "Configuração publicada: versão " + saved.number() + ", " + changes.size()
                        + (changes.size() == 1 ? " alteração" : " alterações") + summary(changes));
        published.evictAfterCommit(saved.clinicId());
        return view(saved);
    }

    @Override
    @Transactional
    public void discardDraft() {
        versions.findByStatus(Status.DRAFT).ifPresent(draft -> {
            versions.delete(draft);
            audit.record("config.draft-discarded", "config_version", draft.id(), "Rascunho descartado");
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionSummary> history() {
        return versions.findAllByOrderByNumberDesc().stream().map(ConfigVersionEntity::summary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Version version(UUID id) {
        return view(own(id));
    }

    @Override
    @Transactional
    public Version restore(UUID id) {
        ConfigVersionEntity source = own(id);
        if (source.status() == Status.DRAFT) {
            return view(source);
        }
        ConfigVersionEntity draft = currentDraft();
        draft.replaceDocument(source.document(), clock.instant());
        ConfigVersionEntity saved = versions.saveAndFlush(draft);
        audit.record("config.restored", "config_version", saved.id(),
                "Versão " + source.number() + " copiada para o rascunho");
        return view(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Change> diff(UUID from, UUID to) {
        return KnowledgeDocument.diff(KnowledgeDocument.fromJson(own(from).document()),
                KnowledgeDocument.fromJson(own(to).document()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Change> draftChanges() {
        Optional<ConfigVersionEntity> draft = versions.findByStatus(Status.DRAFT);
        if (draft.isEmpty()) {
            return List.of();
        }
        return KnowledgeDocument.diff(versions.findByStatus(Status.PUBLISHED)
                        .map(p -> KnowledgeDocument.fromJson(p.document())).orElse(null),
                KnowledgeDocument.fromJson(draft.get().document()));
    }

    @Override
    @Transactional
    public Version importYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new DomainValidationException("file", "arquivo vazio");
        }
        Map<String, Object> document = KnowledgeDocument.fromYaml(yaml);
        KnowledgeDocument.read(document, clock);
        ConfigVersionEntity draft = currentDraft();
        draft.replaceDocument(KnowledgeDocument.toJson(document), clock.instant());
        ConfigVersionEntity saved = versions.saveAndFlush(draft);
        audit.record("config.imported", "config_version", saved.id(), "Arquivo YAML importado para o rascunho");
        return view(saved);
    }

    @Override
    @Transactional
    public ClinicKnowledge draftKnowledge() {
        return KnowledgeDocument.read(KnowledgeDocument.fromJson(currentDraft().document()), clock);
    }

    @Override
    public Map<String, Object> blankDocument() {
        return KnowledgeDocument.blank();
    }

    private ConfigVersionEntity currentDraft() {
        return versions.findByStatus(Status.DRAFT).orElseGet(() -> {
            String document = versions.findByStatus(Status.PUBLISHED)
                    .map(ConfigVersionEntity::document)
                    .orElseGet(() -> KnowledgeDocument.toJson(KnowledgeDocument.blank()));
            return versions.saveAndFlush(new ConfigVersionEntity(versions.maxNumber() + 1, document, author(),
                    clock.instant()));
        });
    }

    private ConfigVersionEntity own(UUID id) {
        return versions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Versão da configuração", id));
    }

    private static Version view(ConfigVersionEntity entity) {
        return new Version(entity.summary(), KnowledgeDocument.fromJson(entity.document()));
    }

    private static UUID author() {
        return StaffUser.current().map(StaffUser::userId).orElse(null);
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String clean = note.strip();
        if (clean.length() > MAX_NOTE) {
            throw new DomainValidationException("note", "até " + MAX_NOTE + " caracteres");
        }
        return clean;
    }

    /** Até três chaves alteradas, no resumo da auditoria (nunca valores: podem ter dados de contato). */
    private static String summary(List<Change> changes) {
        if (changes.isEmpty()) {
            return "";
        }
        List<String> keys = changes.stream().map(Change::key).limit(3).toList();
        return " (" + String.join(", ", keys) + (changes.size() > 3 ? ", …" : "") + ")";
    }
}
