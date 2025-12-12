package com.bf4invest.controller;

import com.bf4invest.model.AuditLog;
import com.bf4invest.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    
    private final AuditLogRepository auditLogRepository;
    
    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<AuditLog> logs = auditLogRepository.findAll();
        
        if (entityType != null) {
            logs = logs.stream().filter(l -> l.getEntityType().equals(entityType)).toList();
        }
        if (entityId != null) {
            logs = logs.stream().filter(l -> l.getEntityId() != null && l.getEntityId().equals(entityId)).toList();
        }
        if (userId != null) {
            logs = logs.stream().filter(l -> l.getUserId().equals(userId)).toList();
        }
        if (from != null) {
            logs = logs.stream().filter(l -> l.getTimestamp() != null && !l.getTimestamp().isBefore(from)).toList();
        }
        if (to != null) {
            logs = logs.stream().filter(l -> l.getTimestamp() != null && !l.getTimestamp().isAfter(to)).toList();
        }
        
        return ResponseEntity.ok(logs);
    }
}




