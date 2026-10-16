package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.exception.AccountNotActiveException;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainNotFoundException.class)
    ProblemDetail notFound(DomainNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Resource not found");
        return p;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail business(BusinessRuleException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("Business rule violation");
        return p;
    }

    // Cuota agotada → 429 para que el cliente espere; configuración o caída → 503
    @ExceptionHandler(AiProviderException.class)
    ProblemDetail aiProvider(AiProviderException ex) {
        HttpStatus status = ex.reason() == AiProviderException.Reason.RATE_LIMITED
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.SERVICE_UNAVAILABLE;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        p.setTitle("AI provider error");
        p.setProperty("reason", ex.reason().name());
        return p;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        p.setTitle("Unauthorized");
        return p;
    }

    @ExceptionHandler(com.riwi.skillbridge.domain.exception.UnauthorizedActionException.class)
    ProblemDetail unauthorizedAction(com.riwi.skillbridge.domain.exception.UnauthorizedActionException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        p.setTitle("Forbidden action");
        return p;
    }

    @ExceptionHandler(AccountNotActiveException.class)
    ProblemDetail accountNotActive(AccountNotActiveException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        p.setTitle("Account not active");
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst().orElse("Solicitud inválida");
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        p.setTitle("Validation error");
        return p;
    }

    // JSON mal formado o un valor que no existe (p. ej. nivel "EXPERTO" o día "LUNES"): 400 con el campo y los valores válidos
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        String detail = "El cuerpo de la solicitud no tiene un formato válido";
        if (ex.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType() != null) {
            String field = invalid.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .reduce((a, b) -> b.startsWith("[") ? a + b : a + "." + b)
                    .orElse("valor");
            detail = field + ": valor no permitido '" + invalid.getValue() + "'";
            if (invalid.getTargetType().isEnum()) {
                detail += ". Usa uno de: " + java.util.Arrays.stream(invalid.getTargetType().getEnumConstants())
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", "));
            }
        }
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        p.setTitle("Bad request");
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        p.setTitle("Bad request");
        return p;
    }
}
