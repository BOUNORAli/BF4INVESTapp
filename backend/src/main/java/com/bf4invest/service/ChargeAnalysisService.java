package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.model.Charge;
import com.bf4invest.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChargeAnalysisService {
    
    private final ChargeRepository chargeRepository;
    
    public DashboardKpiResponse.ChargeAnalysis analyzeCharges(LocalDate from, LocalDate to) {
        List<Charge> charges = chargeRepository.findAll();
        
        // Filtrer par période si nécessaire
        if (from != null || to != null) {
            charges = charges.stream()
                    .filter(c -> {
                        if (c.getDateEcheance() == null) return false;
                        if (from != null && c.getDateEcheance().isBefore(from)) return false;
                        if (to != null && c.getDateEcheance().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        double totalCharges = charges.stream()
                .mapToDouble(c -> c.getMontant() != null ? c.getMontant() : 0.0)
                .sum();
        
        double chargesPrevues = charges.stream()
                .filter(c -> "PREVUE".equals(c.getStatut()))
                .mapToDouble(c -> c.getMontant() != null ? c.getMontant() : 0.0)
                .sum();
        
        double chargesPayees = charges.stream()
                .filter(c -> "PAYEE".equals(c.getStatut()))
                .mapToDouble(c -> c.getMontant() != null ? c.getMontant() : 0.0)
                .sum();
        
        // Répartition par catégorie
        Map<String, Double> montantsParCategorie = charges.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCategorie() != null ? c.getCategorie() : "AUTRE",
                        Collectors.summingDouble(c -> c.getMontant() != null ? c.getMontant() : 0.0)
                ));
        
        List<DashboardKpiResponse.ChargeCategoryStat> repartitionParCategorie = montantsParCategorie.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(entry -> DashboardKpiResponse.ChargeCategoryStat.builder()
                        .categorie(entry.getKey())
                        .montant(entry.getValue())
                        .pourcentage(totalCharges > 0 ? (entry.getValue() / totalCharges) * 100 : 0.0)
                        .build())
                .collect(Collectors.toList());
        
        // Échéances
        LocalDate today = LocalDate.now();
        double echeances0_30 = 0.0;
        double echeances31_60 = 0.0;
        double echeancesPlus60 = 0.0;
        int count0_30 = 0;
        int count31_60 = 0;
        int countPlus60 = 0;
        
        for (Charge c : charges) {
            if (c.getDateEcheance() != null && "PREVUE".equals(c.getStatut())) {
                long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, c.getDateEcheance());
                double montant = c.getMontant() != null ? c.getMontant() : 0.0;
                
                if (daysUntil >= 0 && daysUntil <= 30) {
                    echeances0_30 += montant;
                    count0_30++;
                } else if (daysUntil > 30 && daysUntil <= 60) {
                    echeances31_60 += montant;
                    count31_60++;
                } else if (daysUntil > 60) {
                    echeancesPlus60 += montant;
                    countPlus60++;
                }
            }
        }
        
        List<DashboardKpiResponse.ChargeEcheance> echeances = Arrays.asList(
                DashboardKpiResponse.ChargeEcheance.builder()
                        .periode("0-30j")
                        .montant(echeances0_30)
                        .nombre(count0_30)
                        .build(),
                DashboardKpiResponse.ChargeEcheance.builder()
                        .periode("31-60j")
                        .montant(echeances31_60)
                        .nombre(count31_60)
                        .build(),
                DashboardKpiResponse.ChargeEcheance.builder()
                        .periode("60j+")
                        .montant(echeancesPlus60)
                        .nombre(countPlus60)
                        .build()
        );
        
        return DashboardKpiResponse.ChargeAnalysis.builder()
                .totalCharges(totalCharges)
                .chargesPrevues(chargesPrevues)
                .chargesPayees(chargesPayees)
                .repartitionParCategorie(repartitionParCategorie)
                .echeances(echeances)
                .build();
    }
}

