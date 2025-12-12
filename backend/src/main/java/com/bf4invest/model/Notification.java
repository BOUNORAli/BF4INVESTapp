package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    private String id;
    
    private String type; // FA_NON_REGLEE, ALERTE_TVA, etc.
    private String referenceId; // ID de la facture, BC, etc.
    
    private String niveau; // info, warning, critique
    private String titre;
    private String message;
    
    private boolean read;
    
    private LocalDateTime createdAt;
}




