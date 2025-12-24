package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "charges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Charge {
    @Id
    private String id;

    private String libelle;        // Ex: Loyer, Salaire, Internet...
    private String categorie;      // Ex: LOYER, SALAIRES, TRANSPORT...
    private Double montant;        // Montant de la charge
    private LocalDate dateEcheance;

    /**
     * PREVUE | PAYEE
     */
    private String statut;
    private LocalDate datePaiement;

    /**
     * Imposable (déductible fiscalement) / non imposable
     */
    private Boolean imposable;

    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


