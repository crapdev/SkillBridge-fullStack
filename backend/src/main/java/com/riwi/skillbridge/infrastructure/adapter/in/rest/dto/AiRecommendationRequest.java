package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param goal    mensaje actual del usuario
 * @param history mensajes anteriores de la conversación (opcional), del más antiguo al más reciente
 */
public record AiRecommendationRequest(
        @NotBlank @Size(max = 1000) String goal,
        @Size(max = 50) List<@NotNull @Valid HistoryMessage> history
) {

    public record HistoryMessage(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 4000) String content
    ) {
        ChatMessage toDomain() {
            return "user".equals(role) ? ChatMessage.user(content) : ChatMessage.assistant(content);
        }
    }

    public List<ChatMessage> historyAsDomain() {
        return history == null ? List.of() : history.stream().map(HistoryMessage::toDomain).toList();
    }
}
