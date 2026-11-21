package com.clinicavet.assistant.clinicknowledge.api;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuração da clínica pela web (RF-S11 a RF-S19, ADR-0026). A visão geral (avisos de validade e de rascunho) é
 * de qualquer funcionário logado; editar exige {@code config:edit}; publicar exige {@code config:publish}.
 */
@RestController
@RequestMapping("/app/api/config")
class ConfigController {

    /** Limite do arquivo YAML importado: o modelo completo da clínica fictícia tem uns 6 KB. */
    static final int MAX_IMPORT = 200_000;

    private final ClinicConfig config;
    private final com.clinicavet.assistant.clinicknowledge.domain.BusinessTemplates templates;
    private final com.clinicavet.assistant.clinicknowledge.domain.ConfigImports imports;

    ConfigController(ClinicConfig config, com.clinicavet.assistant.clinicknowledge.domain.BusinessTemplates templates,
                     com.clinicavet.assistant.clinicknowledge.domain.ConfigImports imports) {
        this.config = config;
        this.templates = templates;
        this.imports = imports;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('config:edit')")
    List<com.clinicavet.assistant.clinicknowledge.domain.BusinessTemplates.Template> templates() {
        return templates.list();
    }

    @PostMapping("/templates/{type}/apply")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version applyTemplate(@PathVariable com.clinicavet.assistant.clinicknowledge.domain.BusinessTemplates.Type type) {
        return templates.apply(type);
    }

    @PostMapping("/import/services")
    @PreAuthorize("hasAuthority('config:edit')")
    com.clinicavet.assistant.clinicknowledge.domain.ConfigImports.ServicesResult importServices(
            @Valid @RequestBody ImportRequest body) {
        return imports.importServices(body.content());
    }

    @PostMapping("/import/whatsapp")
    @PreAuthorize("hasAuthority('config:edit')")
    com.clinicavet.assistant.clinicknowledge.domain.ConfigImports.ChatAnalysis analyzeWhatsApp(
            @Valid @RequestBody ChatRequest body) {
        return imports.analyzeWhatsApp(body.files(), body.clinicSender());
    }

    @PostMapping("/import/faq")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version addFaq(@Valid @RequestBody FaqRequest body) {
        return imports.addFaq(body.items());
    }

    record ChatRequest(@NotNull @Size(min = 1, max = 20) List<String> files, @Size(max = 60) String clinicSender) {
    }

    record FaqRequest(@NotNull List<com.clinicavet.assistant.clinicknowledge.domain.ConfigImports.FaqItem> items) {
    }

    @GetMapping("/overview")
    ClinicConfig.Overview overview() {
        return config.overview();
    }

    @GetMapping("/draft")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version draft() {
        return config.draft();
    }

    @PutMapping("/draft")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version saveDraft(@Valid @RequestBody SaveDraftRequest body) {
        return config.saveDraft(body.document(), body.revision());
    }

    @DeleteMapping("/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('config:edit')")
    void discardDraft() {
        config.discardDraft();
    }

    @GetMapping("/draft/changes")
    @PreAuthorize("hasAuthority('config:edit')")
    List<ClinicConfig.Change> draftChanges() {
        return config.draftChanges();
    }

    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('config:publish')")
    ClinicConfig.Version publish(@Valid @RequestBody(required = false) PublishRequest body) {
        return config.publish(body == null ? null : body.note());
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('config:edit')")
    List<ClinicConfig.VersionSummary> history() {
        return config.history();
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version version(@PathVariable UUID id) {
        return config.version(id);
    }

    @GetMapping("/versions/{from}/diff/{to}")
    @PreAuthorize("hasAuthority('config:edit')")
    List<ClinicConfig.Change> diff(@PathVariable UUID from, @PathVariable UUID to) {
        return config.diff(from, to);
    }

    @PostMapping("/versions/{id}/restore")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version restore(@PathVariable UUID id) {
        return config.restore(id);
    }

    @PostMapping("/import/yaml")
    @PreAuthorize("hasAuthority('config:edit')")
    ClinicConfig.Version importYaml(@Valid @RequestBody ImportRequest body) {
        return config.importYaml(body.content());
    }

    @GetMapping("/blank")
    @PreAuthorize("hasAuthority('config:edit')")
    Map<String, Object> blank() {
        return config.blankDocument();
    }

    record SaveDraftRequest(@NotNull Map<String, Object> document, @NotNull Long revision) {
    }

    record PublishRequest(@Size(max = 200) String note) {
    }

    record ImportRequest(@NotBlank @Size(max = MAX_IMPORT) String content) {
    }
}
