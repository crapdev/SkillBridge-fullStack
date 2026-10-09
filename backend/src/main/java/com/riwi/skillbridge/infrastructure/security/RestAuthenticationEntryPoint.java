package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responde 401 (en lugar del 403 por defecto) cuando una ruta protegida se pide
 * sin token o con un token que el JwtAuthenticationFilter no pudo validar.
 * El cuerpo sigue el formato ProblemDetail usado por GlobalExceptionHandler.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Nunca se registra el contenido de la cabecera Authorization, solo si venía o no
        boolean hasBearer = request.getHeader("Authorization") != null;
        log.debug("Acceso no autenticado a {} {} (cabecera Authorization presente: {})",
                request.getMethod(), request.getRequestURI(), hasBearer);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Token ausente o invalido","instance":"%s"}"""
                .formatted(request.getRequestURI().replace("\"", "")));
    }
}
