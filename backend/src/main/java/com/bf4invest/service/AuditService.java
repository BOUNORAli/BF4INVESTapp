package com.bf4invest.service;

import com.bf4invest.model.AuditLog;
import com.bf4invest.model.User;
import com.bf4invest.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    
    public void logAction(String action, String entityType, String entityId, Object oldValue, Object newValue) {
        String username = SecurityContextHolder.getContext().getAuthentication() != null ?
                SecurityContextHolder.getContext().getAuthentication().getName() : "SYSTEM";
        
        AuditLog log = AuditLog.builder()
                .userId(username)
                .userName(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .timestamp(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }
    
    public void logCreate(String entityType, String entityId, Object newValue) {
        logAction("CREATE", entityType, entityId, null, newValue);
    }
    
    public void logUpdate(String entityType, String entityId, Object oldValue, Object newValue) {
        logAction("UPDATE", entityType, entityId, oldValue, newValue);
    }
    
    public void logDelete(String entityType, String entityId, Object oldValue) {
        logAction("DELETE", entityType, entityId, oldValue, null);
    }
}




