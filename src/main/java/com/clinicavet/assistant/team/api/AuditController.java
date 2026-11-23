package com.clinicavet.assistant.team.api;

import com.clinicavet.assistant.platform.AuditTrail;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.team.StaffDirectory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta da auditoria (revisão pré-piloto, E2; RF-S07). Só leitura, com {@code audit:view}: não há rota que altere
 * ou apague registros. Mostra quem fez, o quê, quando e sobre o quê; nunca conteúdo de mensagem (o resumo gravado já
 * não leva dado de saúde nem senha). O nome de quem agiu vem do cadastro da equipe, também de quem foi desativado.
 */
@RestController
@RequestMapping("/app/api/audit")
@PreAuthorize("hasAuthority('audit:view')")
class AuditController {

    static final int MAX_PAGE_SIZE = 100;

    /** @param actorName {@code null} = o sistema (assistente, canal, tarefa agendada) */
    record Item(UUID id, Instant at, UUID actorId, String actorName, String action, String targetType, String targetId,
                String summary) {
    }

    record PageResponse(List<Item> items, int page, int size, long total) {
    }

    private final AuditTrail trail;
    private final StaffDirectory directory;
    private final Clock clock;

    AuditController(AuditTrail trail, StaffDirectory directory, Clock clock) {
        this.trail = trail;
        this.directory = directory;
        this.clock = clock;
    }

    /**
     * @param from   primeiro dia (no fuso da clínica); padrão: 30 dias atrás
     * @param to     último dia, inclusive; padrão: hoje
     * @param action prefixo da ação, ex.: {@code conversation.} ou {@code request.status_changed}
     */
    @GetMapping
    PageResponse search(@RequestParam(required = false) LocalDate from,
                        @RequestParam(required = false) LocalDate to,
                        @RequestParam(required = false) UUID actorId,
                        @RequestParam(required = false) String action,
                        @RequestParam(required = false) String targetType,
                        @RequestParam(required = false) String targetId,
                        @RequestParam(defaultValue = "false") boolean withoutLogins,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainValidationException("size", "página de 1 a " + MAX_PAGE_SIZE + " itens");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate first = from == null ? today.minusDays(29) : from;
        LocalDate last = to == null ? today : to;
        if (last.isBefore(first)) {
            throw new DomainValidationException("to", "antes do início do período");
        }
        AuditTrail.Page found = trail.search(new AuditTrail.Filter(first.atStartOfDay(clock.getZone()).toInstant(),
                last.plusDays(1).atStartOfDay(clock.getZone()).toInstant(), actorId, action, targetType, targetId, withoutLogins),
                page, size);
        Set<UUID> actors = new HashSet<>();
        found.items().forEach(e -> actorOf(e).ifPresent(actors::add));
        Map<UUID, String> names = directory.names(actors);
        return new PageResponse(found.items().stream().map(e -> {
            UUID actor = actorOf(e).orElse(null);
            return new Item(e.id(), e.occurredAt(), actor,
                    actor == null ? null : names.getOrDefault(actor, "Conta removida"), e.action(), e.targetType(),
                    e.targetId(), e.summary());
        }).toList(), found.page(), found.size(), found.total());
    }

    /**
     * Quem agiu. No login, aceite de convite e troca de senha a pessoa ainda não está na sessão quando o registro é
     * gravado: o autor é a própria conta, que fica como alvo.
     */
    private static java.util.Optional<UUID> actorOf(AuditTrail.Entry e) {
        if (e.actorUserId() != null) {
            return java.util.Optional.of(e.actorUserId());
        }
        if (e.action().startsWith("auth.") && "user".equals(e.targetType()) && e.targetId() != null) {
            try {
                return java.util.Optional.of(UUID.fromString(e.targetId()));
            } catch (IllegalArgumentException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }
}
