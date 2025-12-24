package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "exercices_comptables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciceComptable {
    @Id
    private String id;
    
    private String code; // Ex: "2024", "2024-2025"
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private StatutExercice statut; // OUVERT, CLOTURE
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum StatutExercice {
        OUVERT,   // Exercice en cours
        CLOTURE   // Exercice clôturé
    }
}

