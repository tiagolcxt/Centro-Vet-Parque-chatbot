package com.clinicavet.assistant.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private enum Dummy { CLOSED }

    @Test
    void notFound_is404() {
        ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException("Conversation", "abc"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties()).containsEntry("resource", "Conversation");
    }

    @Test
    void invalidTransition_is409() {
        ProblemDetail problem = handler.handleInvalidTransition(
                new InvalidStateTransitionException("Conversation", Dummy.CLOSED, "waitForHuman"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("currentState", "CLOSED");
    }

    @Test
    void concurrentUpdate_is409() {
        ProblemDetail problem = handler.handleConcurrentUpdate(
                new ObjectOptimisticLockingFailureException("ConversationEntity", "id"));

        assertThat(problem.getStatus()).isEqualTo(409);
    }

    @Test
    void missingFieldsAndValidation_are422() {
        ProblemDetail missing = handler.handleMissingFields(new MissingRequiredFieldsException(List.of("PET")));
        ProblemDetail invalid = handler.handleDomainValidation(new DomainValidationException("name", "vazio"));

        assertThat(missing.getStatus()).isEqualTo(422);
        assertThat(missing.getProperties()).containsEntry("missingFields", List.of("PET"));
        assertThat(invalid.getStatus()).isEqualTo(422);
        assertThat(invalid.getProperties()).containsEntry("field", "name");
    }

    @Test
    void unexpected_is500_withoutLeakingTheMessage() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("dado pessoal: Maria"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("Maria");
        assertThat(problem.getProperties()).containsKey("errorId");
    }
}
