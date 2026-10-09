package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private DatabaseUserDetailsService userDetailsService;

    // Mockeamos los objetos HTTP nativos
    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        // Vital: Limpiar el contexto de seguridad antes de cada prueba para que no haya interferencias
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // Vital: Limpiar el contexto después de cada prueba
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChain_WhenNoAuthHeaderIsPresent() throws ServletException, IOException {
        // Arrange: Simulamos una petición a una ruta pública (sin header Authorization)
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act: Ejecutamos el filtro
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert: Verificamos que la petición siga su curso y no se toque el SecurityContext
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Verificamos que jamás se intentó usar el servicio JWT
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void shouldAuthenticateUser_WhenTokenIsValid() throws ServletException, IOException {
        // Arrange: Simulamos un token válido
        String token = "token_perfecto";
        String email = "daniel@email.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);

        // Creamos un usuario de prueba (UserDetails de Spring)
        UserDetails mockUser = new User(email, "password", Collections.emptyList());
        when(userDetailsService.loadUserByUsername(email)).thenReturn(mockUser);
        when(jwtService.isTokenValid(token, email)).thenReturn(true);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert: El usuario debe quedar registrado en el contexto de Spring Security
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(email, SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldClearContextAndContinue_WhenJwtThrowsException() throws ServletException, IOException {
        // Arrange: Simulamos un token alterado o expirado
        String token = "token_corrupto";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Forzamos a que el servicio lance una excepción (simulando lo que haría jjwt si falla la firma)
        when(jwtService.extractUsername(token)).thenThrow(new RuntimeException("Firma inválida"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert: El bloque catch debe limpiar el contexto y dejar que la petición siga (para que el EntryPoint lance el 401)
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticate_WhenAccountIsDisabled() throws ServletException, IOException {
        // Arrange: token bien firmado de un proveedor pendiente o rechazado (cuenta deshabilitada)
        String token = "token_de_cuenta_inactiva";
        String email = "proveedor@email.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        UserDetails disabledUser = User.withUsername(email).password("password").roles("PROVIDER").disabled(true).build();
        when(userDetailsService.loadUserByUsername(email)).thenReturn(disabledUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert: no se autentica y la petición sigue para que el EntryPoint responda 401
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
