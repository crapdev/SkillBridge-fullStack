package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Manejo del token CSRF para una SPA (HU-29), según la guía de Spring Security 6 (en la 7 es csrf.spa()).
 * Angular lee la cookie XSRF-TOKEN y devuelve su valor tal cual en la cabecera X-XSRF-TOKEN, así que
 * ese valor se valida sin la máscara XOR que Spring usa para tokens incrustados en HTML.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
        // El token es diferido: pedirlo aquí hace que la cookie XSRF-TOKEN se escriba en cada respuesta
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        // Cabecera (Angular): valor de la cookie sin máscara. Parámetro de formulario: con máscara XOR
        return (StringUtils.hasText(header) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
