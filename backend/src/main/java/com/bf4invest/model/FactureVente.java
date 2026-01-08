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

@Document(collection = "factures_ventes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactureVente {
    @Id
    private String id;
    
    private String numeroFactureVente; // Format: FV-YYYY-NNN
    private LocalDate dateFacture;
    private LocalDate dateEcheance; // Calculé selon délai paramétrable (défaut 30j)
    
    private String bandeCommandeId; // Optionnel: lié à une BC
    private String bcReference; // Référence BC (colonne AFFECTATION de l'Excel)
    private String clientId;
    
    // Type de mouvement (colonne E de l'Excel: "C" pour Client, "F" pour Fournisseur, etc.)
    private String typeMouvement; // "C" = Client, "F" = Fournisseur, "IB", "FB", "CTP", "CTD", etc.
    
    // Nature de la ligne (colonne H de l'Excel)
    private String nature; // "facture", "paiement", "loy", etc.
    
    // Colonnes supplémentaires pour les calculs (colonne A, B, D, F de l'Excel)
    private String colA; // Colonne A (ex: "CAPITAL")
    private String colB; // Colonne B (utilisé dans le calcul du solde)
    private String colD; // Utilisé pour les filtres (ex: "CCA")
    private String colF; // Utilisé dans le calcul du solde pour IB
    
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
    
    // ========== GESTION DES AVOIRS ==========
    
    // Type de facture : "NORMALE" ou "AVOIR"
    private String typeFacture; // Défaut: "NORMALE"
    
    // Flag pour indiquer si c'est un avoir
    private Boolean estAvoir; // Défaut: false
    
    // Référence à la facture d'origine si c'est un avoir
    private String factureOrigineId; // ID de la facture vente annulée
    
    // Numéro de la facture d'origine (pour référence rapide sans jointure)
    private String numeroFactureOrigine;
    
    // Liste des IDs des factures liées à cet avoir (si avoir partiel sur plusieurs factures)
    private List<String> facturesLieesIds;
    
    // ==========================================
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




