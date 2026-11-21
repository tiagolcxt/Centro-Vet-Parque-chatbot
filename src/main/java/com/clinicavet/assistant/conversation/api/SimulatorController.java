package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.ConfigSimulator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** Simulador da configuração (S3-7): responde com o rascunho, sem gravar nada. Exige {@code config:edit}. */
@RestController
@RequestMapping("/app/api/config/simulate")
class SimulatorController {

    private final ConfigSimulator simulator;

    SimulatorController(ConfigSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('config:edit')")
    ConfigSimulator.Result simulate(@Valid @RequestBody SimulateRequest body) {
        List<ConfigSimulator.Line> previous = body.previous() == null ? List.of()
                : body.previous().stream().map(l -> new ConfigSimulator.Line(l.from(), l.text())).toList();
        return simulator.simulate(previous, body.text(), body.at());
    }

    record LineRequest(@NotNull ConfigSimulator.From from, @NotBlank @Size(max = 2000) String text) {
    }

    /** @param at horário local simulado ({@code 2026-11-21T20:00}); vazio = agora */
    record SimulateRequest(@NotBlank @Size(max = 2000) String text,
                           @Size(max = ConfigSimulator.MAX_PREVIOUS) List<@Valid LineRequest> previous,
                           LocalDateTime at) {
    }
}
