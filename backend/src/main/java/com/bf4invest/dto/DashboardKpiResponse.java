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
}




