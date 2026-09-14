package com.tradeflow.notification.service;

import java.util.UUID;

import com.tradeflow.common.api.PageResponse;
import com.tradeflow.notification.dto.NotificationResponse;
import com.tradeflow.notification.entity.Notification;
import com.tradeflow.notification.repository.NotificationRepository;
import com.tradeflow.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getForUser(UUID userId, int page, int size) {
        currentUser.requireSelfOrAdmin(userId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(repository.findAllByUserId(userId, pageable), NotificationService::toResponse);
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getEventId(), notification.getTransferId(),
                notification.getMessage(), notification.getCreatedAt());
    }
}
