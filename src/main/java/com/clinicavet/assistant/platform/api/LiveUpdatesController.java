package com.clinicavet.assistant.platform.api;

import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.platform.infrastructure.LiveUpdatesHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Canal de avisos em tempo real da aplicação web (RF-S29). Exige sessão; avisa só a clínica do funcionário. */
@RestController
class LiveUpdatesController {

    private final LiveUpdatesHub hub;

    LiveUpdatesController(LiveUpdatesHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/app/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events() {
        return hub.open(StaffUser.current().orElseThrow().clinic());
    }
}
