package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BCAnalysisService {
    
    private final BandeCommandeRepository bcRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    public DashboardKpiResponse.BCAnalysis analyzeBCs(LocalDate from, LocalDate to) {
        List<BandeCommande> bcs = bcRepository.findAll();
        
        // Filtrer par période si nécessaire
        if (from != null || to != null) {
            bcs = bcs.stream()
                    .filter(bc -> {
                        if (bc.getDateBC() == null) return false;
                        if (from != null && bc.getDateBC().isBefore(from)) return false;
                        if (to != null && bc.getDateBC().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        int totalBCs = bcs.size();
        int bcsDraft = (int) bcs.stream().filter(bc -> "brouillon".equals(bc.getEtat())).count();
        int bcsSent = (int) bcs.stream().filter(bc -> "envoyee".equals(bc.getEtat())).count();
        int bcsCompleted = (int) bcs.stream().filter(bc -> "complete".equals(bc.getEtat())).count();
        
        // Délai moyen de traitement (de création à complétion)
        List<Long> delais = new ArrayList<>();
        for (BandeCommande bc : bcs) {
            if (bc.getCreatedAt() != null && "complete".equals(bc.getEtat()) && bc.getUpdatedAt() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(
                        bc.getCreatedAt().toLocalDate(),
                        bc.getUpdatedAt().toLocalDate()
                );
                delais.add(days);
            }
        }
        double delaiMoyenTraitement = delais.isEmpty() ? 0.0 : 
                delais.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        // Taux de conversion BC -> Facture
        List<FactureAchat> facturesAchat = factureAchatRepository.findAll();
        List<FactureVente> facturesVente = factureVenteRepository.findAll();
        
        Set<String> bcsAvecFacture = new HashSet<>();
        
        // Créer une map de référence BC -> ID pour la recherche
        Map<String, String> bcRefToIdMap = new HashMap<>();
        for (BandeCommande bc : bcs) {
            if (bc.getNumeroBC() != null) {
                bcRefToIdMap.put(bc.getNumeroBC(), bc.getId());
            }
        }
        
        // Parcourir les factures achat et trouver les BCs liés
        for (FactureAchat fa : facturesAchat) {
            // Chercher par ID d'abord
            if (fa.getBandeCommandeId() != null) {
                bcsAvecFacture.add(fa.getBandeCommandeId());
            }
            // Sinon chercher par référence BC
            else if (fa.getBcReference() != null && bcRefToIdMap.containsKey(fa.getBcReference())) {
                bcsAvecFacture.add(bcRefToIdMap.get(fa.getBcReference()));
            }
        }
        
        // Parcourir les factures vente et trouver les BCs liés
        for (FactureVente fv : facturesVente) {
            // Chercher par ID d'abord
            if (fv.getBandeCommandeId() != null) {
                bcsAvecFacture.add(fv.getBandeCommandeId());
            }
            // Sinon chercher par référence BC
            else if (fv.getBcReference() != null && bcRefToIdMap.containsKey(fv.getBcReference())) {
                bcsAvecFacture.add(bcRefToIdMap.get(fv.getBcReference()));
            }
        }
        
        double tauxConversionBCFacture = totalBCs > 0 ? 
                ((double) bcsAvecFacture.size() / totalBCs) * 100 : 0.0;
        
        // Compter les BCs non facturées et leur montant total
        int bcsNonFacturees = 0;
        double montantBCsNonFacturees = 0.0;
        for (BandeCommande bc : bcs) {
            if (!bcsAvecFacture.contains(bc.getId())) {
                bcsNonFacturees++;
                // Ajouter le montant HT de vente de cette BC (pas encore facturée)
                if (bc.getTotalVenteHT() != null) {
                    montantBCsNonFacturees += bc.getTotalVenteHT();
                }
            }
        }
        
        // Performance par client
        Map<String, BCPerfData> perfParClient = new HashMap<>();
        for (BandeCommande bc : bcs) {
            if (bc.getClientsVente() != null) {
                for (var clientVente : bc.getClientsVente()) {
                    String clientId = clientVente.getClientId();
                    if (clientId != null) {
                        BCPerfData perf = perfParClient.computeIfAbsent(clientId, k -> new BCPerfData());
                        perf.nombreBCs++;
                        perf.montantTotal += clientVente.getTotalVenteHT() != null ? clientVente.getTotalVenteHT() : 0.0;
                        if (bc.getCreatedAt() != null && bc.getUpdatedAt() != null) {
                            long days = java.time.temporal.ChronoUnit.DAYS.between(
                                    bc.getCreatedAt().toLocalDate(),
                                    bc.getUpdatedAt().toLocalDate()
                            );
                            perf.delais.add(days);
                        }
                    }
                }
            } else if (bc.getClientId() != null) {
                // Compatibilité ancienne structure
                BCPerfData perf = perfParClient.computeIfAbsent(bc.getClientId(), k -> new BCPerfData());
                perf.nombreBCs++;
                perf.montantTotal += bc.getTotalVenteHT() != null ? bc.getTotalVenteHT() : 0.0;
                if (bc.getCreatedAt() != null && bc.getUpdatedAt() != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(
                            bc.getCreatedAt().toLocalDate(),
                            bc.getUpdatedAt().toLocalDate()
                    );
                    perf.delais.add(days);
                }
            }
        }
        
        List<DashboardKpiResponse.BCPerformance> performanceParClient = perfParClient.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().montantTotal, e1.getValue().montantTotal))
                .limit(10)
                .map(entry -> {
                    String clientId = entry.getKey();
                    BCPerfData perf = entry.getValue();
                    Client client = clientRepository.findById(clientId).orElse(null);
                    String clientNom = client != null ? client.getNom() : "Client inconnu";
                    double delaiMoyen = perf.delais.isEmpty() ? 0.0 :
                            perf.delais.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    
                    return DashboardKpiResponse.BCPerformance.builder()
                            .partenaireId(clientId)
                            .partenaireNom(clientNom)
                            .nombreBCs(perf.nombreBCs)
                            .montantTotal(perf.montantTotal)
                            .delaiMoyen(delaiMoyen)
                            .build();
                })
                .collect(Collectors.toList());
        
        // Performance par fournisseur
        Map<String, BCPerfData> perfParFournisseur = new HashMap<>();
        for (BandeCommande bc : bcs) {
            String fournisseurId = bc.getFournisseurId();
            if (fournisseurId != null) {
                BCPerfData perf = perfParFournisseur.computeIfAbsent(fournisseurId, k -> new BCPerfData());
                perf.nombreBCs++;
                perf.montantTotal += bc.getTotalAchatHT() != null ? bc.getTotalAchatHT() : 0.0;
                if (bc.getCreatedAt() != null && bc.getUpdatedAt() != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(
                            bc.getCreatedAt().toLocalDate(),
                            bc.getUpdatedAt().toLocalDate()
                    );
                    perf.delais.add(days);
                }
            }
        }
        
        List<DashboardKpiResponse.BCPerformance> performanceParFournisseur = perfParFournisseur.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().montantTotal, e1.getValue().montantTotal))
                .limit(10)
                .map(entry -> {
                    String fournisseurId = entry.getKey();
                    BCPerfData perf = entry.getValue();
                    Supplier supplier = supplierRepository.findById(fournisseurId).orElse(null);
                    String supplierNom = supplier != null ? supplier.getNom() : "Fournisseur inconnu";
                    double delaiMoyen = perf.delais.isEmpty() ? 0.0 :
                            perf.delais.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    
                    return DashboardKpiResponse.BCPerformance.builder()
                            .partenaireId(fournisseurId)
                            .partenaireNom(supplierNom)
                            .nombreBCs(perf.nombreBCs)
                            .montantTotal(perf.montantTotal)
                            .delaiMoyen(delaiMoyen)
                            .build();
                })
                .collect(Collectors.toList());
        
        return DashboardKpiResponse.BCAnalysis.builder()
                .totalBCs(totalBCs)
                .bcsDraft(bcsDraft)
                .bcsSent(bcsSent)
                .bcsCompleted(bcsCompleted)
                .delaiMoyenTraitement(delaiMoyenTraitement)
                .tauxConversionBCFacture(tauxConversionBCFacture)
                .performanceParClient(performanceParClient)
                .performanceParFournisseur(performanceParFournisseur)
                .bcsNonFacturees(bcsNonFacturees)
                .montantBCsNonFacturees(NumberUtils.roundTo2Decimals(montantBCsNonFacturees))
                .build();
    }
    
    private static class BCPerfData {
        int nombreBCs = 0;
        double montantTotal = 0.0;
        List<Long> delais = new ArrayList<>();
    }
}

