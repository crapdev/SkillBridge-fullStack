package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

import static com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase.MAX_HISTORY_MESSAGES;

/**
 * @param goal    mensaje actual del usuario
 * @param history mensajes anteriores de la conversación (opcional), del más antiguo al más reciente
 */
public record AiRecommendationRequest(
        @NotBlank @Size(max = 1000) String goal,
        List<@NotNull @Valid HistoryMessage> history
) {

    /** Largo máximo de cada mensaje del historial que se reenvía al modelo */
    static final int MAX_HISTORY_CONTENT_LENGTH = 4000;

    public AiRecommendationRequest {
        // El front envía toda la conversación guardada: se recorta aquí, antes de validar, para que una
        // conversación larga no termine en 400. Al modelo solo le llegan los últimos mensajes de todas formas
        if (history != null && history.size() > MAX_HISTORY_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        }
    }

    public record HistoryMessage(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank String content
    ) {
        public HistoryMessage {
            // Una respuesta larga del asistente vuelve en el historial: se acorta en lugar de rechazar la petición
            if (content != null && content.length() > MAX_HISTORY_CONTENT_LENGTH) {
                content = content.substring(0, MAX_HISTORY_CONTENT_LENGTH);
            }
        }

        ChatMessage toDomain() {
            return "user".equals(role) ? ChatMessage.user(content) : ChatMessage.assistant(content);
        }
    }

    public List<ChatMessage> historyAsDomain() {
        return history == null ? List.of() : history.stream().map(HistoryMessage::toDomain).toList();
    }
}
