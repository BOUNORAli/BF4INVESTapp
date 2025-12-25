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
    private String bcReference; // Référence BC (colonne AFFECTATION de l'Excel)
    private String fournisseurId;
    
    // Type de mouvement (colonne E de l'Excel: "F" pour Fournisseur, etc.)
    private String typeMouvement; // "F" = Fournisseur, "C" = Client, "IB", "FB", "CTP", "CTD", etc.
    
    // Nature de la ligne (colonne H de l'Excel)
    private String nature; // "facture", "paiement", "loy", etc.
    
    // Colonnes supplémentaires pour les calculs (colonne D, F de l'Excel)
    private String colD; // Utilisé pour les filtres (ex: "CCA")
    private String colF; // Utilisé dans le calcul du solde pour IB
    private String colA; // Colonne A (ex: "CAPITAL")
    private String colB; // Colonne B (utilisé dans le calcul du solde)
    
    private Boolean ajouterAuStock; // Option pour ajouter les quantités au stock (défaut: false)
    
    private List<LineItem> lignes;
    
    private Double totalHT;
    private Double totalTVA;
    private Double totalTTC;
    
    // Taux utilisés dans les calculs
    private Double tvaRate; // Taux TVA (colonne M, ex: 0.20 pour 20%)
    private Double tauxRG; // Taux de remise globale (colonne N, ex: 0.10 pour 10%)
    
    // Champs calculés selon les formules Excel
    private String tvaMois; // Format "mois/année" (ex: "01/2025")
    private Double solde; // Solde calculé selon type mouvement
    private Double totalTTCApresRG; // TTC après remise globale (colonne K)
    private Double totalTTCApresRG_SIGNE; // TTC après RG avec signe (colonne U)
    private Double totalPaiementTTC; // Total paiement TTC (colonne V)
    private Double rgTTC; // Remise globale TTC (colonne W)
    private Double rgHT; // Remise globale HT (colonne X)
    private Double factureHT_YC_RG; // Facture HT incluant RG (colonne Y)
    private Double htPaye; // HT payé (colonne Z)
    private Double tvaFactureYcRg; // TVA facture incluant RG (colonne AA)
    private Double tvaPaye; // TVA payée (colonne AB)
    private Double bilan; // Bilan HT (colonne AC)
    
    private String modePaiement; // virement, cheque, LCN, compensation
    private String etatPaiement; // regle, partiellement_regle, non_regle
    
    private List<Paiement> paiements;
    
    private List<PrevisionPaiement> previsionsPaiement;
    
    private Double montantRestant; // Calculé: totalTTC - somme paiements
    
    // Référence au fichier de facture fournisseur (image ou PDF)
    private String fichierFactureId;      // ID GridFS du fichier facture
    private String fichierFactureNom;     // Nom original du fichier
    private String fichierFactureType;    // MIME type (image/jpeg, image/png, application/pdf)
    private String fichierFactureUrl;     // URL sécurisée Cloudinary
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




