package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ManageMyNotificationsUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.NotificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications/me")
public class NotificationController {
    private final ManageMyNotificationsUseCase useCase;

    public NotificationController(ManageMyNotificationsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "10") int limit, Authentication authentication) {
        return useCase.listMine(authentication.getName(), limit).stream()
            .map(NotificationResponse::from)
            .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", useCase.countUnread(authentication.getName()));
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication authentication) {
        useCase.markAllRead(authentication.getName());
    }
}
