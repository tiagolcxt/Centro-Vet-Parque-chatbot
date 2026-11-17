package com.clinicavet.assistant.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.UUID;

/**
 * Tradução centralizada de erros para ProblemDetail (RFC 9457). Corpo inválido (Bean Validation) vira
 * {@code 422} (DM-11); JSON malformado e demais erros do Spring MVC seguem a classe base ({@code 400}).
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

    @ExceptionHandler(ConcurrentTurnException.class)
    ProblemDetail handleConcurrentTurn(ConcurrentTurnException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Mensagens simultâneas na mesma conversa");
        problem.setProperty("messageRecorded", true);
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
        // Só o tipo e o ponto de origem no código: a mensagem pode conter dados pessoais.
        StackTraceElement origin = ex.getStackTrace().length == 0 ? null : ex.getStackTrace()[0];
        log.error("Erro inesperado [errorId={}, type={}, origem={}]", errorId, ex.getClass().getName(),
                origin == null ? "-" : origin.getClassName() + "." + origin.getMethodName() + ":" + origin.getLineNumber());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Informe o código errorId ao suporte.");
        problem.setTitle("Erro interno");
        problem.setProperty("errorId", errorId);
        return problem;
    }

    /** Corpo da requisição violou Bean Validation: {@code 422}, com os campos e sem os valores. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "Requisição com campos inválidos.");
        problem.setTitle("Valor inválido");
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        problem.setProperty("fields", fields);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }
}
