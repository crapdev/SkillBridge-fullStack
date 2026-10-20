package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.infrastructure.security.AuthCookies;
import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import com.riwi.skillbridge.infrastructure.security.RestAuthenticationEntryPoint;
import com.riwi.skillbridge.infrastructure.security.SpaCsrfTokenRequestHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                 RestAuthenticationEntryPoint entryPoint,
                                 RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // HU-29: con la sesión en una cookie, el navegador la envía solo y hace falta CSRF. El token queda en
                // la cookie legible XSRF-TOKEN y se acepta en la cabecera X-XSRF-TOKEN, la que Angular envía solo
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                // Stripe es un servidor, no un navegador: se autentica con la firma Stripe-Signature
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/payments/webhook"),
                                // Login, registro y logout no actúan sobre una sesión existente; la primera visita aún
                                // no tiene XSRF-TOKEN, y la cookie de sesión SameSite=Strict impide que otro sitio los use
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/**"),
                                // El navegador nunca agrega Authorization por su cuenta: una página ajena no puede falsificarla
                                SecurityConfiguration::hasBearerToken,
                                // Sin cookie de sesión no hay nada que un sitio ajeno pueda aprovechar; así una petición
                                // anónima recibe 401 (falta sesión) y no 403 (falta CSRF)
                                request -> !hasSessionCookie(request)))
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Va antes que /api/auth/** (gana la primera regla que coincide): /me necesita sesión
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers("/error", "/api/auth/**", "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/offerings/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Stripe no envía JWT: el webhook se autentica verificando la firma Stripe-Signature
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        // Reservas, pagos y recomendaciones con IA son exclusivas de clientes
                        .requestMatchers("/api/bookings/**", "/api/payments/**", "/api/ai/**").hasRole("CUSTOMER")
                        // La gestión de mentorías y horarios es exclusiva de proveedores
                        .requestMatchers("/api/provider/**").hasRole("PROVIDER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // La campana es solo del proveedor; cada uno ve únicamente las suyas
                        .requestMatchers("/api/notifications/**").hasRole("PROVIDER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ");
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(c -> AuthCookies.NAME.equals(c.getName()));
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

