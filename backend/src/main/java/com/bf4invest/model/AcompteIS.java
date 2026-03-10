package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "acomptes_is")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcompteIS {
    @Id
    private String id;

    private Integer annee;
    private Integer trimestre;
    private LocalDate dateEcheance;
    private Double montant;
    private LocalDate datePaiement;
    private Double montantPaye;
    private StatutAcompte statut;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum StatutAcompte {
        EN_ATTENTE,
        PAYE,
        EN_RETARD
    }
}
