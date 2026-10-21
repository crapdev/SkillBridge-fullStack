package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.ChatMessage;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {

    static final int MAX_HISTORY_MESSAGES = 10;
    static final String EMPTY_CATALOG_MESSAGE =
        "En este momento no hay mentorías disponibles con horarios libres para recomendar.";

    // Enlace markdown al detalle de una mentoría: [Título](/services/<id>)
    private static final Pattern OFFERING_LINK = Pattern.compile("\\[([^\\]]+)]\\(/services/([^)\\s]+)\\)");

    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings;

    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings) {
        this.ai = ai;
        this.offerings = offerings;
    }

    @Override
    public String recommend(String message, List<ChatMessage> history) {
        // Mismo criterio que el catálogo de HU-17: activas, aprobadas y con horarios futuros libres
        List<Offering> catalog = offerings.findVisible();
        if (catalog.isEmpty()) {
            return EMPTY_CATALOG_MESSAGE;
        }

        List<ChatMessage> conversation = new ArrayList<>(lastMessages(history));
        conversation.add(ChatMessage.user(message));

        return keepOnlyCatalogLinks(ai.recommend(conversation, catalog), catalog);
    }

    private static List<ChatMessage> lastMessages(List<ChatMessage> history) {
        if (history == null) return List.of();
        return history.subList(Math.max(0, history.size() - MAX_HISTORY_MESSAGES), history.size());
    }

    /** Quita los enlaces a mentorías que no están en el catálogo (id inventado por el modelo) y deja solo el título */
    private static String keepOnlyCatalogLinks(String response, List<Offering> catalog) {
        Set<String> visibleIds = catalog.stream().map(o -> o.id().toString()).collect(Collectors.toSet());
        Matcher m = OFFERING_LINK.matcher(response);
        return m.replaceAll(match -> Matcher.quoteReplacement(
            visibleIds.contains(match.group(2).toLowerCase()) ? match.group() : match.group(1)));
    }
}
