package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.ChatMessage;
import java.util.List;

public interface GenerateRecommendationUseCase {

    /** Mensajes anteriores que se envían al modelo; los más antiguos se descartan */
    int MAX_HISTORY_MESSAGES = 10;

    /**
     * @param message mensaje actual del usuario
     * @param history mensajes anteriores de la conversación, del más antiguo al más reciente
     */
    String recommend(String message, List<ChatMessage> history);
}
