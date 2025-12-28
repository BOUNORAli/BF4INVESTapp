package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "solde_global")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoldeGlobal {
    @Id
    private String id;
    
    private Double soldeInitial; // Solde de départ configuré par l'utilisateur
    private Double soldeActuel; // Solde calculé en temps réel
    private LocalDate dateDebut; // Date de début de la comptabilité
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}



