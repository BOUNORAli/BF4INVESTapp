package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    private String id;
    
    private String userId;
    private String userName;
    private String action; // CREATE, UPDATE, DELETE
    private String entityType; // Client, Supplier, BC, etc.
    private String entityId;
    
    private Object oldValue; // JSON de l'ancienne valeur
    private Object newValue; // JSON de la nouvelle valeur
    
    private String ipAddress;
    private String userAgent;
    
    private LocalDateTime timestamp;
}




