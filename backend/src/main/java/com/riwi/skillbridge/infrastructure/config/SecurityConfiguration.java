package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    // Instanciamos el logger para cumplir el criterio de imprimir en modo debug
    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // --- NUEVO: Manejo de excepciones para devolver 401 y hacer el log ---
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    String authHeader = request.getHeader("Authorization");

                    // Registrar a nivel debug sin exponer el token completo
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        String safeToken = token.length() > 8 ? token.substring(0, 8) + "..." : "invalido";
                        log.debug("Fallo de autenticación con token inválido: {}", safeToken);
                    } else {
                        log.debug("Fallo de autenticación: Peti.ción sin token o formato incorrecto");
                    }

                    // Sobrescribir el 403 por defecto por un 401
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autorizado");
                })
            )
            // --------------------------------------------------------------------

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/offerings/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}


//¿Qué hace este cambio?
//Atrapa el rechazo de seguridad: Antes de que Spring lance el 403 a su manera, el AuthenticationEntryPoint interviene.
//
//Cumple el logueo seguro: Lee la cabecera Authorization. Si existe y tiene un token, corta los primeros 8 caracteres y oculta el resto (ej. eyJhGci...), imprimiéndolo solo si el nivel de logs está en DEBUG.
//
//Fuerza el 401: Llama a response.sendError(401) para devolver el código HTTP exacto que te pide la historia de usuario.
