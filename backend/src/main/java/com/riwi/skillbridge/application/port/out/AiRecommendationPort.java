package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.ChatMessage;
import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;

public interface AiRecommendationPort {
    /**
     * @param conversation mensajes a enviar al modelo; el último es el mensaje actual del usuario
     * @param catalog mentorías que el modelo puede recomendar
     */
    String recommend(List<ChatMessage> conversation, List<Offering> catalog);
}
