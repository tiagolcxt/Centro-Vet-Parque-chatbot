package com.clinicavet.assistant.clinicknowledge.api;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Setores (RF-S12). Qualquer funcionário logado lista; criar e alterar exige {@code config:edit}. */
@RestController
@RequestMapping("/app/api/departments")
class DepartmentController {

    private final Departments departments;

    DepartmentController(Departments departments) {
        this.departments = departments;
    }

    @GetMapping
    List<Departments.Department> list() {
        return departments.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('config:edit')")
    Departments.Department create(@Valid @RequestBody CreateRequest body) {
        return departments.create(body.name(), body.kind());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('config:edit')")
    Departments.Department update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        Departments.Department result = departments.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setor", id));
        if (body.name() != null) {
            result = departments.rename(id, body.name());
        }
        if (body.active() != null) {
            result = departments.setActive(id, body.active());
        }
        return result;
    }

    record CreateRequest(@NotBlank @Size(max = 60) String name, @NotNull Departments.Kind kind) {
    }

    record UpdateRequest(@Size(max = 60) String name, Boolean active) {
    }
}
