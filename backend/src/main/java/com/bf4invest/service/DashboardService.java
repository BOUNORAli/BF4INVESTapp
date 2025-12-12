package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final BandeCommandeRepository bcRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    public DashboardKpiResponse getKPIs(LocalDate from, LocalDate to) {
        List<FactureVente> facturesVente = filterFacturesVenteByDateRange(
                factureVenteRepository.findAll(), from, to);
        List<FactureAchat> facturesAchat = filterFacturesAchatByDateRange(
                factureAchatRepository.findAll(), from, to);
        
        // CA - Calculer depuis les BCs (plus complet car inclut les commandes non encore facturées)
        // Les BCs contiennent la source de vérité pour les ventes
        List<BandeCommande> allBCs = bcRepository.findAll();
        double caHTFromBCs = allBCs.stream()
                .mapToDouble(bc -> bc.getTotalVenteHT() != null ? bc.getTotalVenteHT() : 0.0)
                .sum();
        
        // CA depuis les factures de vente (pour compatibilité)
        double caHTFromInvoices = facturesVente.stream()
                .mapToDouble(f -> f.getTotalHT() != null ? f.getTotalHT() : 0.0)
                .sum();
        
        // Utiliser la valeur la plus élevée ou celle des BCs si disponible
        double caHT = caHTFromBCs > 0 ? caHTFromBCs : caHTFromInvoices;
        
        double caTTC = facturesVente.stream()
                .mapToDouble(f -> f.getTotalTTC() != null ? f.getTotalTTC() : 0.0)
                .sum();
        
        // Achats
        double totalAchatsHT = facturesAchat.stream()
                .mapToDouble(f -> f.getTotalHT() != null ? f.getTotalHT() : 0.0)
                .sum();
        double totalAchatsTTC = facturesAchat.stream()
                .mapToDouble(f -> f.getTotalTTC() != null ? f.getTotalTTC() : 0.0)
                .sum();
        
        // Marges - Calculer à partir des BCs pour une meilleure précision
        // Les BCs contiennent la source de vérité pour les prix d'achat et de vente
        List<BandeCommande> bcs = bcRepository.findAll();
        double totalAchatHTFromBCs = bcs.stream()
                .mapToDouble(bc -> bc.getTotalAchatHT() != null ? bc.getTotalAchatHT() : 0.0)
                .sum();
        double totalVenteHTFromBCs = bcs.stream()
                .mapToDouble(bc -> bc.getTotalVenteHT() != null ? bc.getTotalVenteHT() : 0.0)
                .sum();
        
        // Utiliser les BCs pour calculer la marge (plus précis)
        // Sinon utiliser les factures comme fallback
        double margeTotale;
        double totalAchatsHTForMargin;
        
        if (totalVenteHTFromBCs > 0) {
            // Utiliser les BCs comme source principale
            margeTotale = totalVenteHTFromBCs - totalAchatHTFromBCs;
            totalAchatsHTForMargin = totalAchatHTFromBCs;
        } else {
            // Fallback sur les factures si pas de BCs
            margeTotale = caHT - totalAchatsHT;
            totalAchatsHTForMargin = totalAchatsHT;
        }
        
        double margeMoyenne = totalAchatsHTForMargin > 0 ? (margeTotale / totalAchatsHTForMargin) * 100 : 0.0;
        
        // TVA
        double tvaCollectee = facturesVente.stream()
                .mapToDouble(f -> f.getTotalTVA() != null ? f.getTotalTVA() : 0.0)
                .sum();
        double tvaDeductible = facturesAchat.stream()
                .mapToDouble(f -> f.getTotalTVA() != null ? f.getTotalTVA() : 0.0)
                .sum();
        
        // Impayés
        DashboardKpiResponse.ImpayesInfo impayes = calculateImpayes(facturesAchat, facturesVente);
        
        // Factures en retard
        long facturesEnRetard = factureAchatRepository.findByDateEcheanceLessThanEqual(LocalDate.now())
                .stream()
                .filter(f -> !"regle".equals(f.getEtatPaiement()))
                .count();
        
        // CA Mensuel
        List<DashboardKpiResponse.MonthlyData> caMensuel = calculateCaMensuel(facturesVente, from, to);
        
        // Top Fournisseurs et Clients
        List<DashboardKpiResponse.FournisseurClientStat> topFournisseurs = calculateTopFournisseurs(facturesAchat);
        List<DashboardKpiResponse.FournisseurClientStat> topClients = calculateTopClients(facturesVente);
        
        return DashboardKpiResponse.builder()
                .caHT(caHT)
                .caTTC(caTTC)
                .totalAchatsHT(totalAchatsHT)
                .totalAchatsTTC(totalAchatsTTC)
                .margeTotale(margeTotale)
                .margeMoyenne(margeMoyenne)
                .tvaCollectee(tvaCollectee)
                .tvaDeductible(tvaDeductible)
                .impayes(impayes)
                .facturesEnRetard((int) facturesEnRetard)
                .caMensuel(caMensuel)
                .topFournisseurs(topFournisseurs)
                .topClients(topClients)
                .build();
    }
    
    private List<FactureVente> filterFacturesVenteByDateRange(List<FactureVente> factures, LocalDate from, LocalDate to) {
        if (from == null && to == null) return factures;
        return factures.stream()
                .filter(f -> {
                    if (f.getDateFacture() == null) return false;
                    if (from != null && f.getDateFacture().isBefore(from)) return false;
                    if (to != null && f.getDateFacture().isAfter(to)) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }
    
    private List<FactureAchat> filterFacturesAchatByDateRange(List<FactureAchat> factures, LocalDate from, LocalDate to) {
        if (from == null && to == null) return factures;
        return factures.stream()
                .filter(f -> {
                    if (f.getDateFacture() == null) return false;
                    if (from != null && f.getDateFacture().isBefore(from)) return false;
                    if (to != null && f.getDateFacture().isAfter(to)) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }
    
    private DashboardKpiResponse.ImpayesInfo calculateImpayes(
            List<FactureAchat> facturesAchat, List<FactureVente> facturesVente) {
        LocalDate today = LocalDate.now();
        
        double impayes0_30 = 0.0;
        double impayes31_60 = 0.0;
        double impayesPlus60 = 0.0;
        
        // Factures achat impayées
        for (FactureAchat fa : facturesAchat) {
            if (!"regle".equals(fa.getEtatPaiement()) && fa.getDateEcheance() != null) {
                double montant = fa.getMontantRestant() != null ? fa.getMontantRestant() : fa.getTotalTTC();
                long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(fa.getDateEcheance(), today);
                
                if (daysOverdue <= 30) {
                    impayes0_30 += montant;
                } else if (daysOverdue <= 60) {
                    impayes31_60 += montant;
                } else {
                    impayesPlus60 += montant;
                }
            }
        }
        
        // Factures vente impayées
        for (FactureVente fv : facturesVente) {
            if (!"regle".equals(fv.getEtatPaiement()) && fv.getDateEcheance() != null) {
                double montant = fv.getMontantRestant() != null ? fv.getMontantRestant() : fv.getTotalTTC();
                long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(fv.getDateEcheance(), today);
                
                if (daysOverdue <= 30) {
                    impayes0_30 += montant;
                } else if (daysOverdue <= 60) {
                    impayes31_60 += montant;
                } else {
                    impayesPlus60 += montant;
                }
            }
        }
        
        return DashboardKpiResponse.ImpayesInfo.builder()
                .totalImpayes(impayes0_30 + impayes31_60 + impayesPlus60)
                .impayes0_30(impayes0_30)
                .impayes31_60(impayes31_60)
                .impayesPlus60(impayesPlus60)
                .build();
    }
    
    private List<DashboardKpiResponse.MonthlyData> calculateCaMensuel(
            List<FactureVente> factures, LocalDate from, LocalDate to) {
        Map<String, Double> caByMonth = new LinkedHashMap<>();
        Map<String, Double> margeByMonth = new LinkedHashMap<>();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        
        for (FactureVente fv : factures) {
            if (fv.getDateFacture() != null) {
                String month = fv.getDateFacture().format(formatter);
                double ht = fv.getTotalHT() != null ? fv.getTotalHT() : 0.0;
                caByMonth.merge(month, ht, Double::sum);
            }
        }
        
        List<DashboardKpiResponse.MonthlyData> result = new ArrayList<>();
        caByMonth.forEach((month, ca) -> {
            result.add(DashboardKpiResponse.MonthlyData.builder()
                    .mois(month)
                    .caHT(ca)
                    .marge(0.0) // Simplified
                    .build());
        });
        
        return result;
    }
    
    private List<DashboardKpiResponse.FournisseurClientStat> calculateTopFournisseurs(
            List<FactureAchat> factures) {
        Map<String, Double> montants = new HashMap<>();
        
        for (FactureAchat fa : factures) {
            if (fa.getFournisseurId() != null) {
                double montant = fa.getTotalTTC() != null ? fa.getTotalTTC() : 0.0;
                montants.merge(fa.getFournisseurId(), montant, Double::sum);
            }
        }
        
        return montants.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    String nom = supplierRepository.findById(entry.getKey())
                            .map(s -> s.getNom())
                            .orElse("Inconnu");
                    return DashboardKpiResponse.FournisseurClientStat.builder()
                            .id(entry.getKey())
                            .nom(nom)
                            .montant(entry.getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    private List<DashboardKpiResponse.FournisseurClientStat> calculateTopClients(
            List<FactureVente> factures) {
        Map<String, Double> montants = new HashMap<>();
        
        for (FactureVente fv : factures) {
            if (fv.getClientId() != null) {
                double montant = fv.getTotalTTC() != null ? fv.getTotalTTC() : 0.0;
                montants.merge(fv.getClientId(), montant, Double::sum);
            }
        }
        
        return montants.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    String nom = clientRepository.findById(entry.getKey())
                            .map(c -> c.getNom())
                            .orElse("Inconnu");
                    return DashboardKpiResponse.FournisseurClientStat.builder()
                            .id(entry.getKey())
                            .nom(nom)
                            .montant(entry.getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }
}


