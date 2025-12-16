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

@Document(collection = "factures_achats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactureAchat {
    @Id
    private String id;
    
    private String numeroFactureAchat; // Format: FA-YYYY-NNN
    private LocalDate dateFacture;
    private LocalDate dateEcheance; // Calculé: dateFacture + 2 mois
    
    private String bandeCommandeId; // Optionnel: lié à une BC
    private String fournisseurId;
    
    private Boolean ajouterAuStock; // Option pour ajouter les quantités au stock (défaut: false)
    
    private List<LineItem> lignes;
    
    private Double totalHT;
    private Double totalTVA;
    private Double totalTTC;
    
    private String modePaiement; // virement, cheque, LCN, compensation
    private String etatPaiement; // regle, partiellement_regle, non_regle
    
    private List<Paiement> paiements;
    
    private Double montantRestant; // Calculé: totalTTC - somme paiements
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




