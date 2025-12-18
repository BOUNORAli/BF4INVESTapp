package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "paiements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Paiement {
    @Id
    private String id;
    
    private String factureAchatId; // Optionnel
    private String factureVenteId; // Optionnel
    private String bcReference; // Référence BC associée (colonne AFFECTATION de l'Excel)
    
    // Type de mouvement (colonne E de l'Excel)
    private String typeMouvement; // "C" = Client, "F" = Fournisseur, "FB", "CTP", "CTD", etc.
    
    // Nature de la ligne (colonne H de l'Excel)
    private String nature; // "paiement", "facture", etc.
    
    // Colonnes supplémentaires pour les calculs
    private String colD; // Utilisé pour les filtres (ex: "CCA")
    
    private LocalDate date;
    private Double montant; // Montant TTC brut (colonne L)
    private String mode; // virement, cheque, LCN, compensation, especes
    private String reference; // Numéro de chèque, référence virement, etc.
    
    // Taux utilisés dans les calculs
    private Double tvaRate; // Taux TVA (colonne M, ex: 0.20 pour 20%)
    
    // Champs calculés selon les formules Excel
    private Double totalPaiementTTC; // Total paiement TTC calculé (colonne V)
    private Double htPaye; // HT payé (colonne Z)
    private Double tvaPaye; // TVA payée (colonne AB)
    
    // Soldes après ce paiement
    private Double soldeGlobalApres; // Solde global après ce paiement
    private Double soldePartenaireApres; // Solde partenaire (client/fournisseur) après ce paiement
    
    private String notes;
    
    private LocalDateTime createdAt;
}




