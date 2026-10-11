package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.Reason;
import com.riwi.skillbridge.domain.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeepSeekAiAdapter implements AiRecommendationPort {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiAdapter.class);

    // Spring AI convierte las respuestas de error en excepciones con el mensaje "HTTP <código> - <cuerpo>"
    private static final Pattern HTTP_STATUS = Pattern.compile("^HTTP (\\d{3})\\b");

    static final String CONFIGURATION_MESSAGE = "El asistente de IA no está configurado correctamente; avisa al administrador";
    static final String RATE_LIMITED_MESSAGE = "El asistente de IA recibió demasiadas consultas; espera un momento e inténtalo de nuevo";
    static final String UNAVAILABLE_MESSAGE = "DeepSeek no está disponible en este momento; inténtalo de nuevo";

    private final ChatClient chatClient;

    public DeepSeekAiAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String recommend(String goal, List<Offering> offerings) {
        String catalog = offerings.stream()
            .map(o -> "- %s [%s]: %s".formatted(o.title(), o.category(), o.description()))
            .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Eres el asistente de SkillBridge AI. Recomienda como máximo 3 servicios del catálogo
                que ayuden al usuario a lograr su objetivo. Explica brevemente por qué y propone un
                siguiente paso. No inventes servicios que no estén en el catálogo.

                Objetivo del usuario:
                %s

                Catálogo disponible:
                %s
                """.formatted(goal, catalog);

        String response;
        try {
            response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        } catch (RuntimeException ex) {
            throw translate(ex);
        }

        if (response == null || response.isBlank()) {
            log.warn("DeepSeek devolvió una respuesta vacía");
            throw new AiProviderException(Reason.UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        return response;
    }

    private AiProviderException translate(RuntimeException ex) {
        Integer status = httpStatus(ex);
        Reason reason = status == null ? Reason.UNAVAILABLE
            : status == 401 || status == 403 ? Reason.CONFIGURATION
            : status == 429 ? Reason.RATE_LIMITED
            : Reason.UNAVAILABLE;

        // Solo el motivo, el código y el tipo de excepción: el cuerpo de error de DeepSeek puede
        // incluir parte de la API key y el prompt contiene el objetivo que escribió el usuario
        log.warn("Fallo del proveedor de IA: motivo={}, status={}, excepcion={}",
            reason, status == null ? "sin respuesta" : status, ex.getClass().getSimpleName());

        String message = switch (reason) {
            case CONFIGURATION -> CONFIGURATION_MESSAGE;
            case RATE_LIMITED -> RATE_LIMITED_MESSAGE;
            case UNAVAILABLE -> UNAVAILABLE_MESSAGE;
        };
        return new AiProviderException(reason, message);
    }

    /** Código HTTP que devolvió DeepSeek, o null si no hubo respuesta (timeout, red, DNS…) */
    private static Integer httpStatus(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                Matcher m = HTTP_STATUS.matcher(t.getMessage());
                if (m.find()) return Integer.valueOf(m.group(1));
            }
        }
        return null;
    }
}
