package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentAnalysisService {
    
    private final PaiementRepository paiementRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    
    public DashboardKpiResponse.PaymentAnalysis analyzePayments(LocalDate from, LocalDate to) {
        List<Paiement> paiements = paiementRepository.findAll();
        
        // Filtrer par période si nécessaire
        if (from != null || to != null) {
            paiements = paiements.stream()
                    .filter(p -> {
                        if (p.getDate() == null) return false;
                        if (from != null && p.getDate().isBefore(from)) return false;
                        if (to != null && p.getDate().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        // Séparer encaissements (clients) et décaissements (fournisseurs)
        double totalEncaissements = NumberUtils.roundTo2Decimals(paiements.stream()
                .filter(p -> p.getFactureVenteId() != null)
                .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                .sum());
        
        double totalDecaissements = NumberUtils.roundTo2Decimals(paiements.stream()
                .filter(p -> p.getFactureAchatId() != null)
                .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                .sum());
        
        // Répartition par mode de paiement
        Map<String, Double> montantsParMode = paiements.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getMode() != null ? p.getMode() : "AUTRE",
                        Collectors.summingDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                ));
        
        double totalPaiements = totalEncaissements + totalDecaissements;
        List<DashboardKpiResponse.PaymentModeStat> repartitionParMode = montantsParMode.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> DashboardKpiResponse.PaymentModeStat.builder()
                        .mode(entry.getKey())
                        .montant(entry.getValue())
                        .pourcentage(totalPaiements > 0 ? (entry.getValue() / totalPaiements) * 100 : 0.0)
                        .build())
                .collect(Collectors.toList());
        
        // Délais moyens de paiement
        double delaiMoyenPaiementClient = NumberUtils.roundTo2Decimals(calculateDelaiMoyenPaiementClient(from, to));
        double delaiMoyenPaiementFournisseur = NumberUtils.roundTo2Decimals(calculateDelaiMoyenPaiementFournisseur(from, to));
        
        // DSO et DPO
        double dso = NumberUtils.roundTo2Decimals(calculateDSO(from, to));
        double dpo = NumberUtils.roundTo2Decimals(calculateDPO(from, to));
        
        // Evolution mensuelle
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Double> encaissementsParMois = paiements.stream()
                .filter(p -> p.getFactureVenteId() != null && p.getDate() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getDate().format(formatter),
                        Collectors.summingDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                ));
        
        Map<String, Double> decaissementsParMois = paiements.stream()
                .filter(p -> p.getFactureAchatId() != null && p.getDate() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getDate().format(formatter),
                        Collectors.summingDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                ));
        
        Set<String> allMonths = new HashSet<>();
        allMonths.addAll(encaissementsParMois.keySet());
        allMonths.addAll(decaissementsParMois.keySet());
        
        List<DashboardKpiResponse.MonthlyPaymentData> evolutionMensuelle = allMonths.stream()
                .sorted()
                .map(month -> DashboardKpiResponse.MonthlyPaymentData.builder()
                        .mois(month)
                        .encaissements(encaissementsParMois.getOrDefault(month, 0.0))
                        .decaissements(decaissementsParMois.getOrDefault(month, 0.0))
                        .build())
                .collect(Collectors.toList());
        
        return DashboardKpiResponse.PaymentAnalysis.builder()
                .totalEncaissements(totalEncaissements)
                .totalDecaissements(totalDecaissements)
                .repartitionParMode(repartitionParMode)
                .delaiMoyenPaiementClient(delaiMoyenPaiementClient)
                .delaiMoyenPaiementFournisseur(delaiMoyenPaiementFournisseur)
                .dso(dso)
                .dpo(dpo)
                .evolutionMensuelle(evolutionMensuelle)
                .build();
    }
    
    private double calculateDelaiMoyenPaiementClient(LocalDate from, LocalDate to) {
        List<FactureVente> factures = factureVenteRepository.findAll();
        if (from != null || to != null) {
            factures = factures.stream()
                    .filter(f -> {
                        if (f.getDateFacture() == null) return false;
                        if (from != null && f.getDateFacture().isBefore(from)) return false;
                        if (to != null && f.getDateFacture().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        List<Long> delais = new ArrayList<>();
        for (FactureVente fv : factures) {
            if (fv.getDateFacture() != null && fv.getPaiements() != null && !fv.getPaiements().isEmpty()) {
                for (Paiement p : fv.getPaiements()) {
                    if (p.getDate() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(fv.getDateFacture(), p.getDate());
                        delais.add(days);
                    }
                }
            }
        }
        
        return delais.isEmpty() ? 0.0 : delais.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    private double calculateDelaiMoyenPaiementFournisseur(LocalDate from, LocalDate to) {
        List<FactureAchat> factures = factureAchatRepository.findAll();
        if (from != null || to != null) {
            factures = factures.stream()
                    .filter(f -> {
                        if (f.getDateFacture() == null) return false;
                        if (from != null && f.getDateFacture().isBefore(from)) return false;
                        if (to != null && f.getDateFacture().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        List<Long> delais = new ArrayList<>();
        for (FactureAchat fa : factures) {
            if (fa.getDateFacture() != null && fa.getPaiements() != null && !fa.getPaiements().isEmpty()) {
                for (Paiement p : fa.getPaiements()) {
                    if (p.getDate() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(fa.getDateFacture(), p.getDate());
                        delais.add(days);
                    }
                }
            }
        }
        
        return delais.isEmpty() ? 0.0 : delais.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    private double calculateDSO(LocalDate from, LocalDate to) {
        // Days Sales Outstanding = (Comptes clients / CA) * nombre de jours
        List<FactureVente> factures = factureVenteRepository.findAll();
        if (from != null || to != null) {
            factures = factures.stream()
                    .filter(f -> {
                        if (f.getDateFacture() == null) return false;
                        if (from != null && f.getDateFacture().isBefore(from)) return false;
                        if (to != null && f.getDateFacture().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        double totalCA = NumberUtils.roundTo2Decimals(factures.stream()
                .mapToDouble(f -> f.getTotalHT() != null ? f.getTotalHT() : 0.0)
                .sum());
        
        double comptesClients = NumberUtils.roundTo2Decimals(factures.stream()
                .filter(f -> !"regle".equals(f.getEtatPaiement()))
                .mapToDouble(f -> f.getMontantRestant() != null ? f.getMontantRestant() : f.getTotalTTC() != null ? f.getTotalTTC() : 0.0)
                .sum());
        
        if (totalCA == 0) return 0.0;
        
        long daysInPeriod = from != null && to != null ? 
                java.time.temporal.ChronoUnit.DAYS.between(from, to) : 365;
        
        return NumberUtils.roundTo2Decimals((comptesClients / totalCA) * daysInPeriod);
    }
    
    private double calculateDPO(LocalDate from, LocalDate to) {
        // Days Payable Outstanding = (Comptes fournisseurs / Achats) * nombre de jours
        List<FactureAchat> factures = factureAchatRepository.findAll();
        if (from != null || to != null) {
            factures = factures.stream()
                    .filter(f -> {
                        if (f.getDateFacture() == null) return false;
                        if (from != null && f.getDateFacture().isBefore(from)) return false;
                        if (to != null && f.getDateFacture().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        double totalAchats = NumberUtils.roundTo2Decimals(factures.stream()
                .mapToDouble(f -> f.getTotalHT() != null ? f.getTotalHT() : 0.0)
                .sum());
        
        double comptesFournisseurs = NumberUtils.roundTo2Decimals(factures.stream()
                .filter(f -> !"regle".equals(f.getEtatPaiement()))
                .mapToDouble(f -> f.getMontantRestant() != null ? f.getMontantRestant() : f.getTotalTTC() != null ? f.getTotalTTC() : 0.0)
                .sum());
        
        if (totalAchats == 0) return 0.0;
        
        long daysInPeriod = from != null && to != null ? 
                java.time.temporal.ChronoUnit.DAYS.between(from, to) : 365;
        
        return NumberUtils.roundTo2Decimals((comptesFournisseurs / totalAchats) * daysInPeriod);
    }
}

