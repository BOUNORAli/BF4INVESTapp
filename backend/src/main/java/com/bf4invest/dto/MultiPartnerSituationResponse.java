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
public class MultiPartnerSituationResponse {
    
    // Liste des partenaires sélectionnés
    private List<PartnerSituationResponse.PartnerInfo> partners;
    
    // Période de calcul
    private LocalDate dateFrom;
    private LocalDate dateTo;
    
    // Liste consolidée de toutes les factures (triées par date)
    private List<FactureDetailWithPartner> facturesConsolidees;
    
    // Liste consolidée de toutes les prévisions (triées par date)
    private List<PrevisionDetailWithPartner> previsionsConsolidees;
    
    // Totaux globaux (somme de tous les partenaires)
    private TotauxGlobaux totauxGlobaux;
    
    // Totaux par partenaire (Map<partnerId, Totaux>)
    private Map<String, PartnerSituationResponse.Totaux> totauxParPartenaire;
    
    // Situations individuelles par partenaire (pour mode groupé)
    private List<PartnerSituationResponse> situationsParPartenaire;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactureDetailWithPartner {
        private String partnerId;
        private String partnerNom;
        private String partnerType; // "CLIENT" ou "FOURNISSEUR"
        private PartnerSituationResponse.FactureDetail facture;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrevisionDetailWithPartner {
        private String partnerId;
        private String partnerNom;
        private String partnerType; // "CLIENT" ou "FOURNISSEUR"
        private PartnerSituationResponse.PrevisionDetail prevision;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotauxGlobaux {
        private Double totalFactureTTC;
        private Double totalFactureHT;
        private Double totalTVA;
        private Double totalPaye;
        private Double totalRestant;
        private Double soldeGlobal;
        private Integer nombreFactures;
        private Integer nombreFacturesPayees;
        private Integer nombreFacturesEnAttente;
        private Integer nombreFacturesEnRetard;
        private Integer nombrePrevisions;
        private Integer nombrePrevisionsRealisees;
        private Integer nombrePrevisionsEnRetard;
        private Integer nombrePartenaires;
    }
}
