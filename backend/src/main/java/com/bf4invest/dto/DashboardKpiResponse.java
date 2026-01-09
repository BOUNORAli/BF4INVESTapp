package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKpiResponse {
    private double caHT;
    private double caTTC;
    private double totalAchatsHT;
    private double totalAchatsTTC;
    private double margeTotale;
    private double margeMoyenne;
    private double tvaCollectee;
    private double tvaDeductible;
    
    private ImpayesInfo impayes;
    private int facturesEnRetard;
    
    private List<MonthlyData> caMensuel;
    private List<FournisseurClientStat> topFournisseurs;
    private List<FournisseurClientStat> topClients;
    
    // Nouvelles métriques pour les sections avancées
    private ChargeAnalysis chargeAnalysis;
    private TreasuryForecast treasuryForecast;
    private PaymentAnalysis paymentAnalysis;
    private ProductPerformance productPerformance;
    private BCAnalysis bcAnalysis;
    private BalanceHistory balanceHistory;
    private AdvancedCharts advancedCharts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpayesInfo {
        private double totalImpayes;
        private double impayes0_30;
        private double impayes31_60;
        private double impayesPlus60;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyData {
        private String mois;
        private double caHT;
        private double marge;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FournisseurClientStat {
        private String id;
        private String nom;
        private double montant;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeAnalysis {
        private double totalCharges;
        private double chargesPrevues;
        private double chargesPayees;
        private List<ChargeCategoryStat> repartitionParCategorie;
        private List<ChargeEcheance> echeances;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeCategoryStat {
        private String categorie;
        private double montant;
        private double pourcentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeEcheance {
        private String periode; // "0-30j", "31-60j", "60j+"
        private double montant;
        private int nombre;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TreasuryForecast {
        private double soldeActuel;
        private List<ForecastData> previsions3Mois;
        private List<EcheanceDetail> echeancesClients;
        private List<EcheanceDetail> echeancesFournisseurs;
        private List<String> alertes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastData {
        private LocalDate date;
        private double soldePrevu;
        private double encaissementsPrevu;
        private double decaissementsPrevu;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EcheanceDetail {
        private LocalDate date;
        private String type; // "CLIENT" ou "FOURNISSEUR"
        private String partenaire;
        private double montant;
        private String statut; // "PREVU", "REALISE", "EN_RETARD"
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentAnalysis {
        private double totalEncaissements;
        private double totalDecaissements;
        private List<PaymentModeStat> repartitionParMode;
        private double delaiMoyenPaiementClient;
        private double delaiMoyenPaiementFournisseur;
        private double dso; // Days Sales Outstanding
        private double dpo; // Days Payable Outstanding
        private List<MonthlyPaymentData> evolutionMensuelle;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentModeStat {
        private String mode;
        private double montant;
        private double pourcentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPaymentData {
        private String mois;
        private double encaissements;
        private double decaissements;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPerformance {
        private List<ProductStat> top10ParMarge;
        private List<ProductStat> top10ParVolume;
        private List<ProductABC> analyseABC;
        private double tauxRotationMoyen;
        private List<ProductAlert> alertesStock;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductStat {
        private String id;
        private String refArticle;
        private String nom;
        private double marge;
        private double volume;
        private double margePourcentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductABC {
        private String categorie; // "A", "B", "C"
        private double pourcentageCA;
        private int nombreProduits;
        private List<String> produits;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAlert {
        private String produitId;
        private String produitNom;
        private String type; // "STOCK_FAIBLE", "STOCK_EPUISE", "PAS_DE_VENTE"
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BCAnalysis {
        private int totalBCs;
        private int bcsDraft;
        private int bcsSent;
        private int bcsCompleted;
        private double delaiMoyenTraitement;
        private double tauxConversionBCFacture;
        private List<BCPerformance> performanceParClient;
        private List<BCPerformance> performanceParFournisseur;
        // Informations sur les BCs non facturées pour diagnostic
        private int bcsNonFacturees;
        private double montantBCsNonFacturees; // Montant HT des BCs non facturées
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BCPerformance {
        private String partenaireId;
        private String partenaireNom;
        private int nombreBCs;
        private double montantTotal;
        private double delaiMoyen;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceHistory {
        private List<BalanceMovement> mouvements;
        private double soldeInitial;
        private double soldeActuel;
        private List<BalanceByPartner> soldeParPartenaire;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceMovement {
        private LocalDate date;
        private String type; // "FACTURE_VENTE", "FACTURE_ACHAT", "PAIEMENT_CLIENT", "PAIEMENT_FOURNISSEUR", "CHARGE"
        private String reference;
        private String partenaire;
        private double montant;
        private double soldeAvant;
        private double soldeApres;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceByPartner {
        private String partenaireId;
        private String partenaireNom;
        private String partenaireType; // "CLIENT" ou "FOURNISSEUR"
        private double solde;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdvancedCharts {
        private List<MarginEvolution> evolutionMarges;
        private List<CAChargeCorrelation> correlationCACharges;
        private GrowthIndicators indicateursCroissance;
        private List<MonthlyPerformance> heatmapMensuelle;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarginEvolution {
        private String mois;
        private double marge;
        private double margePourcentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CAChargeCorrelation {
        private String mois;
        private double ca;
        private double charges;
        private double ratio;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthIndicators {
        private double croissanceMoM; // Month over Month
        private double croissanceYoY; // Year over Year
        private double croissanceMoyenne;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPerformance {
        private String mois;
        private double score; // Score composite de performance (0-100)
        private String niveau; // "EXCELLENT", "BON", "MOYEN", "FAIBLE"
    }
}




