package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.in.ListAvailableSlotsUseCase;
import com.riwi.skillbridge.application.port.in.ListMyBookingsUseCase;
import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.service.AuthService;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.AiController;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.AuthController;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.BookingController;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.OfferingController;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA: login y acceso a rutas protegidas sin token / con token inválido.
 * Levanta la cadena de seguridad real (SecurityConfiguration + JwtAuthenticationFilter +
 * RestAuthenticationEntryPoint + JwtService) junto con AuthService y GlobalExceptionHandler;
 * solo se simulan los puertos de persistencia.
 */
@WebMvcTest(controllers = {AuthController.class, BookingController.class, OfferingController.class, AiController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtService.class, AuthService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + AuthSecurityIntegrationTest.SECRET,
        "app.jwt.expiration=3600000",
        "app.cors.allowed-origins=http://localhost:4200",
        "logging.level.com.riwi.skillbridge.infrastructure.security=DEBUG"
})
@ExtendWith(OutputCaptureExtension.class)
class AuthSecurityIntegrationTest {

    static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String EMAIL = "daniel@email.com";
    private static final String PASSWORD = "password123";
    private static final String HASH = "hashed_password";
    private static final String LOGIN_ERROR = "Email inexistente o contrasena erronea";
    private static final String BOOKING_BODY = """
            {"offeringId":"%s","scheduledAt":"%s"}"""
            .formatted(UUID.randomUUID(), Instant.now().plus(2, ChronoUnit.DAYS));

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    @MockitoBean UserRepositoryPort users;
    @MockitoBean PasswordHasherPort passwords;
    @MockitoBean DatabaseUserDetailsService userDetailsService;
    @MockitoBean CreateBookingUseCase createBooking;
    @MockitoBean ListMyBookingsUseCase listMyBookings;
    @MockitoBean CancelBookingUseCase cancelBooking;
    @MockitoBean ListOfferingsUseCase listOfferings;
    @MockitoBean ListAvailableSlotsUseCase listAvailableSlots;
    @MockitoBean GenerateRecommendationUseCase recommendations;

    @BeforeEach
    void setUp() {
        UserAccount account = new UserAccount(UUID.randomUUID(), "Daniel", EMAIL, HASH, Role.CUSTOMER);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwords.matches(PASSWORD, HASH)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(User.withUsername(EMAIL).password(HASH).roles("CUSTOMER").build());
        when(listOfferings.listActive()).thenReturn(List.of());
        when(recommendations.recommend(anyString())).thenReturn("Te recomiendo Mentoría Java Backend");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private ResultActions createBooking(String authorization) throws Exception {
        var request = post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING_BODY);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mvc.perform(request);
    }

    private static void assertUnauthorizedProblem(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Nested
    class Login {

        @Test
        void validCredentials_returnBearerTokenUsableOnProtectedRoutes() throws Exception {
            String body = login(EMAIL, PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andReturn().getResponse().getContentAsString();

            String token = body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            assertThat(jwtService.extractUsername(token)).isEqualTo(EMAIL);
            createBooking("Bearer " + token).andExpect(status().isCreated());
        }

        @Test
        void unknownEmail_returns401WithGenericMessage() throws Exception {
            login("fantasma@email.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(LOGIN_ERROR));
        }

        @Test
        void wrongPassword_returns401WithGenericMessage() throws Exception {
            login(EMAIL, "wrong_password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(LOGIN_ERROR));
        }

        @Test
        void unknownEmailAndWrongPassword_areIndistinguishable() throws Exception {
            var unknownEmail = login("fantasma@email.com", PASSWORD).andReturn().getResponse();
            var wrongPassword = login(EMAIL, "wrong_password").andReturn().getResponse();

            assertThat(unknownEmail.getStatus()).isEqualTo(wrongPassword.getStatus());
            assertThat(unknownEmail.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
        }

        @Test
        void malformedPayload_returns400() throws Exception {
            login("no-es-un-email", "").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class ProtectedRoutes {

        @Test
        void withoutToken_returns401() throws Exception {
            assertUnauthorizedProblem(createBooking(null));
        }

        @Test
        void withNonBearerScheme_returns401() throws Exception {
            assertUnauthorizedProblem(createBooking("Basic ZGFuaWVsOnBhc3N3b3Jk"));
        }

        @Test
        void withMalformedToken_returns401() throws Exception {
            assertUnauthorizedProblem(createBooking("Bearer esto.no.es-un-jwt"));
        }

        @Test
        void withTokenSignedByAnotherKey_returns401() throws Exception {
            String forged = Jwts.builder()
                    .subject(EMAIL)
                    .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                    .signWith(Jwts.SIG.HS256.key().build())
                    .compact();
            assertUnauthorizedProblem(createBooking("Bearer " + forged));
        }

        @Test
        void withExpiredToken_returns401() throws Exception {
            String expired = Jwts.builder()
                    .subject(EMAIL)
                    .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                    .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                    .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                    .compact();
            assertUnauthorizedProblem(createBooking("Bearer " + expired));
        }

        @Test
        void withValidTokenOfDeletedUser_returns401() throws Exception {
            when(userDetailsService.loadUserByUsername(anyString()))
                    .thenThrow(new UsernameNotFoundException("Usuario no encontrado"));
            assertUnauthorizedProblem(createBooking("Bearer " + jwtService.generate(EMAIL, "CUSTOMER")));
        }

        @Test
        void withValidToken_isAuthorized() throws Exception {
            createBooking("Bearer " + jwtService.generate(EMAIL, "CUSTOMER")).andExpect(status().isCreated());
        }

        @Test
        void publicRoute_withInvalidToken_isStillAccessible() throws Exception {
            mvc.perform(get("/api/offerings").header("Authorization", "Bearer token-corrupto"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class RoleAuthorization {

        private static final String PROVIDER_EMAIL = "proveedor@email.com";
        private static final String ADMIN_EMAIL = "admin@email.com";

        @BeforeEach
        void otherRoles() {
            when(userDetailsService.loadUserByUsername(PROVIDER_EMAIL))
                    .thenReturn(User.withUsername(PROVIDER_EMAIL).password(HASH).roles("PROVIDER").build());
            when(userDetailsService.loadUserByUsername(ADMIN_EMAIL))
                    .thenReturn(User.withUsername(ADMIN_EMAIL).password(HASH).roles("ADMIN").build());
        }

        private ResultActions recommend(String authorization) throws Exception {
            var request = post("/api/ai/recommendations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"goal":"Quiero aprender Spring Boot para trabajar como backend"}""");
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            return mvc.perform(request);
        }

        private static void assertForbiddenProblem(ResultActions result) throws Exception {
            result.andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }

        @Test
        void visitor_cannotUseAi() throws Exception {
            assertUnauthorizedProblem(recommend(null));
        }

        @Test
        void customer_canUseAi() throws Exception {
            recommend("Bearer " + jwtService.generate(EMAIL, "CUSTOMER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recommendation").exists());
        }

        @Test
        void provider_cannotBookNorUseAi() throws Exception {
            String token = "Bearer " + jwtService.generate(PROVIDER_EMAIL, "PROVIDER");
            assertForbiddenProblem(createBooking(token));
            assertForbiddenProblem(recommend(token));
        }

        @Test
        void admin_cannotBookNorUseAi() throws Exception {
            String token = "Bearer " + jwtService.generate(ADMIN_EMAIL, "ADMIN");
            assertForbiddenProblem(createBooking(token));
            assertForbiddenProblem(recommend(token));
        }

        @Test
        void roleIsTakenFromDatabase_notFromTokenClaim() throws Exception {
            // Un token con el claim role=CUSTOMER no sirve si en la base de datos el usuario es PROVIDER
            String token = "Bearer " + jwtService.generate(PROVIDER_EMAIL, "CUSTOMER");
            assertForbiddenProblem(createBooking(token));
        }

        @Test
        void otherRoles_canStillBrowsePublicCatalog() throws Exception {
            mvc.perform(get("/api/offerings")
                            .header("Authorization", "Bearer " + jwtService.generate(PROVIDER_EMAIL, "PROVIDER")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Logging {

        @Test
        void invalidToken_isLoggedWithoutExposingIt(CapturedOutput output) throws Exception {
            String forged = Jwts.builder()
                    .subject(EMAIL)
                    .signWith(Jwts.SIG.HS256.key().build())
                    .compact();

            createBooking("Bearer " + forged).andExpect(status().isUnauthorized());

            assertThat(output.getOut()).contains("JWT rechazado en /api/bookings");
            // Ni el token completo ni ninguna de sus partes (header, payload, firma) aparecen en el log
            assertThat(output.getOut()).doesNotContain(forged);
            for (String part : forged.split("\\.")) {
                assertThat(output.getOut()).doesNotContain(part);
            }
        }
    }
}
