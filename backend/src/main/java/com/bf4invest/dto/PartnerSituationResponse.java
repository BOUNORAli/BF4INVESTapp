package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerSituationResponse {
    
    // Informations du partenaire
    private PartnerInfo partnerInfo;
    
    // Période de calcul
    private LocalDate dateFrom;
    private LocalDate dateTo;
    
    // Liste des factures
    private List<FactureDetail> factures;
    
    // Liste des prévisions de paiement
    private List<PrevisionDetail> previsions;
    
    // Totaux
    private Totaux totaux;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerInfo {
        private String id;
        private String nom;
        private String ice;
        private String reference;
        private String adresse;
        private String telephone;
        private String email;
        private String rib;
        private String banque; // Pour les fournisseurs
        private String type; // "CLIENT" ou "FOURNISSEUR"
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactureDetail {
        private String id;
        private String numeroFacture;
        private LocalDate dateFacture;
        private LocalDate dateEcheance;
        private Double montantTTC;
        private Double montantHT;
        private Double montantTVA;
        private Double montantPaye;
        private Double montantRestant;
        private String statut; // "PAYEE", "PARTIELLE", "EN_ATTENTE", "EN_RETARD"
        private Boolean estAvoir;
        private String numeroFactureOrigine; // Si c'est un avoir
        private String bcReference; // Référence BC associée
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrevisionDetail {
        private String id;
        private String factureId;
        private String numeroFacture;
        private LocalDate datePrevue;
        private Double montantPrevu;
        private Double montantPaye;
        private Double montantRestant;
        private String statut; // "PREVU", "REALISE", "EN_RETARD", "PAYEE", "PARTIELLE"
        private String notes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totaux {
        private Double totalFactureTTC;
        private Double totalFactureHT;
        private Double totalTVA;
        private Double totalPaye;
        private Double totalRestant;
        private Double solde; // Solde total du partenaire
        private Integer nombreFactures;
        private Integer nombreFacturesPayees;
        private Integer nombreFacturesEnAttente;
        private Integer nombreFacturesEnRetard;
        private Integer nombrePrevisions;
        private Integer nombrePrevisionsRealisees;
        private Integer nombrePrevisionsEnRetard;
    }
}

