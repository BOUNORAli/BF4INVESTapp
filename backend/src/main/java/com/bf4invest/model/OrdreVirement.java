package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "ordres_virement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdreVirement {
    @Id
    private String id;
    
    private String numeroOV;           // Ex: OV-2025-001
    private LocalDate dateOV;           // Date de création
    private Double montant;             // Montant total
    private String beneficiaireId;      // ID du fournisseur
    private String nomBeneficiaire;     // Nom du fournisseur (dénormalisé)
    private String ribBeneficiaire;    // RIB du bénéficiaire
    private String motif;               // Libellé/Motif
    private List<String> facturesIds;   // IDs des factures concernées
    private String banqueEmettrice;     // Banque émettrice
    private LocalDate dateExecution;    // Date d'exécution prévue
    private String statut;              // EN_ATTENTE, EXECUTE, ANNULE
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

