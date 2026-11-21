package com.clinicavet.assistant.conversation.quickreply;

import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respostas prontas da equipe (S3-16, DA-53): textos que quem responde insere na caixa de entrada ("como chegar",
 * "preço do banho"). Quem responde conversas lista; editar exige {@code config:edit}. Valem na hora, sem publicação, e
 * não passam pela IA: o funcionário revisa antes de enviar.
 */
@RestController
@RequestMapping("/app/api/quick-replies")
class QuickReplyController {

    static final int MAX_PER_CLINIC = 100;

    record QuickReply(UUID id, String title, String body, Instant updatedAt) {
    }

    record QuickReplyRequest(@NotBlank @Size(max = 60) String title, @NotBlank @Size(max = 2000) String body) {
    }

    private final QuickReplyJpaRepository replies;
    private final AuditLog audit;
    private final Clock clock;

    QuickReplyController(QuickReplyJpaRepository replies, AuditLog audit, Clock clock) {
        this.replies = replies;
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('conversation:reply') or hasAuthority('config:edit')")
    @Transactional(readOnly = true)
    List<QuickReply> list() {
        return replies.findAllByOrderByTitleAsc().stream().map(QuickReplyEntity::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('config:edit')")
    @Transactional
    QuickReply create(@Valid @RequestBody QuickReplyRequest body) {
        String title = body.title().strip();
        if (replies.count() >= MAX_PER_CLINIC) {
            throw new DomainValidationException("title", "até " + MAX_PER_CLINIC + " respostas prontas");
        }
        if (replies.existsByTitle(title)) {
            throw new ConflictException("Já existe uma resposta pronta com esse título.");
        }
        QuickReplyEntity saved = replies.save(new QuickReplyEntity(title, body.body().strip(), clock.instant()));
        audit.record("quick-reply.created", "quick_reply", saved.id(), "Resposta pronta criada: " + title);
        return saved.view();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('config:edit')")
    @Transactional
    QuickReply update(@PathVariable UUID id, @Valid @RequestBody QuickReplyRequest body) {
        QuickReplyEntity reply = replies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Resposta pronta", id));
        String title = body.title().strip();
        if (replies.existsByTitleAndIdNot(title, id)) {
            throw new ConflictException("Já existe uma resposta pronta com esse título.");
        }
        reply.change(title, body.body().strip(), clock.instant());
        audit.record("quick-reply.changed", "quick_reply", id, "Resposta pronta alterada: " + title);
        return reply.view();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('config:edit')")
    @Transactional
    void delete(@PathVariable UUID id) {
        QuickReplyEntity reply = replies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Resposta pronta", id));
        replies.delete(reply);
        audit.record("quick-reply.deleted", "quick_reply", id, null);
    }
}
