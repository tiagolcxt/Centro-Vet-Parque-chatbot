package com.clinicavet.assistant.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.UUID;

/**
 * Tradução centralizada de erros para ProblemDetail (RFC 9457). Erros de validação de entrada do
 * Spring MVC são tratados pela classe base.
 * <p>
 * Os logs nunca incluem conteúdo de mensagens nem dados pessoais (DM-12).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                ex.resource() + " não encontrado");
        problem.setTitle("Recurso não encontrado");
        problem.setProperty("resource", ex.resource());
        return problem;
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidStateTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Transição de estado não permitida");
        problem.setProperty("currentState", ex.from());
        problem.setProperty("action", ex.action());
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O recurso foi alterado por outra operação ao mesmo tempo.");
        problem.setTitle("Conflito de concorrência");
        return problem;
    }

    @ExceptionHandler(MissingRequiredFieldsException.class)
    ProblemDetail handleMissingFields(MissingRequiredFieldsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage());
        problem.setTitle("Dados obrigatórios ausentes");
        problem.setProperty("missingFields", ex.missingFields());
        return problem;
    }

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail handleDomainValidation(DomainValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage());
        problem.setTitle("Valor inválido");
        problem.setProperty("field", ex.field());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        // Só o tipo da exceção: a mensagem pode conter dados pessoais.
        log.error("Erro inesperado [errorId={}, type={}]", errorId, ex.getClass().getName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Informe o código errorId ao suporte.");
        problem.setTitle("Erro interno");
        problem.setProperty("errorId", errorId);
        return problem;
    }
}
