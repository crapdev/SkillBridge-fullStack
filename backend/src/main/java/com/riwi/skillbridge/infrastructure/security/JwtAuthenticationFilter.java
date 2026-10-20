package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final DatabaseUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, DatabaseUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Si no hay token (ni Bearer ni cookie), deja pasar la petición (rutas públicas)
        final String jwt = resolveToken(request);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. Extraer el usuario del token
            final String userEmail = jwtService.extractUsername(jwt);

            // 3. Validar y autenticar si no está autenticado aún
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 4. Usamos isTokenValid respetando el contrato de TokenPort
                if (userDetails.isEnabled() && jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 5. Establecer la autenticación en el contexto
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.debug("JWT rechazado en {}: no corresponde al usuario, esta expirado o la cuenta no esta activa", request.getRequestURI());
                }
            }
        } catch (Exception e) {
            // 6. Si el token está expirado, alterado o malformado, limpiamos el contexto.
            // Solo se registra el tipo de error y la ruta: nunca el token ni el mensaje de la
            // excepción (jjwt puede incluir fragmentos del token en él).
            SecurityContextHolder.clearContext();
            log.debug("JWT rechazado en {}: {}", request.getRequestURI(), e.getClass().getSimpleName());
        }

        // 7. Continuar con el siguiente filtro
        filterChain.doFilter(request, response);
    }

    /**
     * El Bearer tiene prioridad porque es explícito (Swagger, curl, scripts): si alguien prueba con otro token,
     * manda ese aunque el navegador tenga su propia cookie. Sin cabecera se usa la cookie HttpOnly (HU-29).
     */
    static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> AuthCookies.NAME.equals(c.getName()) && !c.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
