package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.ChatMessage;
import java.util.List;

public interface GenerateRecommendationUseCase {
    /**
     * @param message mensaje actual del usuario
     * @param history mensajes anteriores de la conversación, del más antiguo al más reciente
     */
    String recommend(String message, List<ChatMessage> history);
}
