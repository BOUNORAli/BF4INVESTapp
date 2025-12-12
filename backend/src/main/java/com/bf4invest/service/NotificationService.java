package com.bf4invest.service;

import com.bf4invest.model.Notification;
import com.bf4invest.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    
    public Notification createNotification(String type, String referenceId, String niveau, 
                                          String titre, String message) {
        Notification notif = Notification.builder()
                .type(type)
                .referenceId(referenceId)
                .niveau(niveau)
                .titre(titre)
                .message(message)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        return notificationRepository.save(notif);
    }
    
    public List<Notification> getUnreadNotifications() {
        return notificationRepository.findByReadFalseOrderByCreatedAtDesc();
    }
    
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public void markAsRead(String id) {
        notificationRepository.findById(id).ifPresent(notif -> {
            notif.setRead(true);
            notificationRepository.save(notif);
        });
    }
    
    public void markAllAsRead() {
        List<Notification> unread = notificationRepository.findByReadFalseOrderByCreatedAtDesc();
        unread.forEach(notif -> {
            notif.setRead(true);
            notificationRepository.save(notif);
        });
    }
}




