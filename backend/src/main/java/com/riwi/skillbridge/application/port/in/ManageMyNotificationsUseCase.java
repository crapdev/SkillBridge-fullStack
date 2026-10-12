package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Notification;

import java.util.List;

public interface ManageMyNotificationsUseCase {
    List<Notification> listMine(String email, int limit);

    long countUnread(String email);

    void markAllRead(String email);
}
