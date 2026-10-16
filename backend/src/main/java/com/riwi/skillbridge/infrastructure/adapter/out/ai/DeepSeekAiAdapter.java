package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.Reason;
import com.riwi.skillbridge.domain.model.ChatMessage;
import com.riwi.skillbridge.domain.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // Va como mensaje de sistema para que lo que escriba el usuario en el chat no reemplace las reglas
    static final String SYSTEM_PROMPT = """
            Eres el asistente de SkillBridge AI y conversas con un cliente que busca mentorías.
            Usa los mensajes anteriores de la conversación como contexto para afinar tus respuestas.

            Reglas:
            - Recomienda como máximo 3 mentorías y solo del catálogo de abajo; nunca inventes mentorías.
            - Cada mentoría que recomiendes debe ir como enlace markdown con su título y su id exacto
              del catálogo, así: [Título](/services/<id>)
            - Explica brevemente por qué encaja con lo que busca el cliente y propone un siguiente paso.
            - Si ninguna mentoría encaja o el catálogo está vacío, dilo con claridad y no recomiendes nada.

            Catálogo de mentorías disponibles para reservar:
            %s
            """;

    @Override
    public String recommend(List<ChatMessage> conversation, List<Offering> catalog) {
        String catalogText = catalog.isEmpty() ? "(no hay mentorías disponibles en este momento)"
            : catalog.stream()
                .map(o -> "- id=%s | %s [%s]: %s".formatted(o.id(), o.title(), o.category(), o.description()))
                .collect(Collectors.joining("\n"));

        List<Message> messages = conversation.stream().map(DeepSeekAiAdapter::toMessage).toList();

        String response;
        try {
            response = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(catalogText))
                .messages(messages)
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

    private static Message toMessage(ChatMessage message) {
        return switch (message.author()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
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
