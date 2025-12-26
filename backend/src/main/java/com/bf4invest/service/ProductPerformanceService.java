package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.LigneAchat;
import com.bf4invest.model.LigneVente;
import com.bf4invest.model.Product;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductPerformanceService {
    
    private final ProductRepository productRepository;
    private final BandeCommandeRepository bcRepository;
    
    public DashboardKpiResponse.ProductPerformance analyzeProducts(LocalDate from, LocalDate to) {
        List<Product> products = productRepository.findAll();
        List<BandeCommande> bcs = bcRepository.findAll();
        
        // Filtrer BCs par période si nécessaire
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
        
        // Calculer les statistiques par produit
        Map<String, ProductStats> productStatsMap = new HashMap<>();
        
        for (BandeCommande bc : bcs) {
            if (bc.getClientsVente() != null) {
                for (var clientVente : bc.getClientsVente()) {
                    if (clientVente.getLignesVente() != null) {
                        for (LigneVente ligneVente : clientVente.getLignesVente()) {
                            String produitRef = ligneVente.getProduitRef();
                            if (produitRef != null) {
                                ProductStats stats = productStatsMap.computeIfAbsent(produitRef, k -> new ProductStats());
                                stats.volume += ligneVente.getQuantiteVendue() != null ? ligneVente.getQuantiteVendue() : 0.0;
                                stats.marge += ligneVente.getMargeTotale() != null ? ligneVente.getMargeTotale() : 0.0;
                                stats.margeUnitaire = ligneVente.getMargeUnitaire() != null ? ligneVente.getMargeUnitaire() : 0.0;
                                stats.margePourcentage = ligneVente.getMargePourcentage() != null ? ligneVente.getMargePourcentage() : 0.0;
                                stats.designation = ligneVente.getDesignation();
                            }
                        }
                    }
                }
            }
            
            // Analyser aussi les lignes d'achat pour le volume total
            if (bc.getLignesAchat() != null) {
                for (LigneAchat ligneAchat : bc.getLignesAchat()) {
                    String produitRef = ligneAchat.getProduitRef();
                    if (produitRef != null) {
                        ProductStats stats = productStatsMap.computeIfAbsent(produitRef, k -> new ProductStats());
                        stats.volumeAchat += ligneAchat.getQuantiteAchetee() != null ? ligneAchat.getQuantiteAchetee() : 0.0;
                    }
                }
            }
        }
        
        // Récupérer les IDs des produits depuis la ref
        Map<String, String> refToIdMap = products.stream()
                .collect(Collectors.toMap(Product::getRefArticle, Product::getId, (a, b) -> a));
        
        // Top 10 par marge
        List<DashboardKpiResponse.ProductStat> top10ParMarge = productStatsMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().marge, e1.getValue().marge))
                .limit(10)
                .map(entry -> {
                    String ref = entry.getKey();
                    ProductStats stats = entry.getValue();
                    return DashboardKpiResponse.ProductStat.builder()
                            .id(refToIdMap.getOrDefault(ref, ""))
                            .refArticle(ref)
                            .nom(stats.designation != null ? stats.designation : ref)
                            .marge(stats.marge)
                            .volume(stats.volume)
                            .margePourcentage(stats.margePourcentage)
                            .build();
                })
                .collect(Collectors.toList());
        
        // Top 10 par volume
        List<DashboardKpiResponse.ProductStat> top10ParVolume = productStatsMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().volume, e1.getValue().volume))
                .limit(10)
                .map(entry -> {
                    String ref = entry.getKey();
                    ProductStats stats = entry.getValue();
                    return DashboardKpiResponse.ProductStat.builder()
                            .id(refToIdMap.getOrDefault(ref, ""))
                            .refArticle(ref)
                            .nom(stats.designation != null ? stats.designation : ref)
                            .marge(stats.marge)
                            .volume(stats.volume)
                            .margePourcentage(stats.margePourcentage)
                            .build();
                })
                .collect(Collectors.toList());
        
        // Analyse ABC (80/20 rule)
        double totalCA = productStatsMap.values().stream()
                .mapToDouble(s -> s.marge)
                .sum();
        
        List<Map.Entry<String, ProductStats>> sortedByMarge = productStatsMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().marge, e1.getValue().marge))
                .collect(Collectors.toList());
        
        double cumulCA = 0.0;
        List<String> produitsA = new ArrayList<>();
        List<String> produitsB = new ArrayList<>();
        List<String> produitsC = new ArrayList<>();
        
        for (Map.Entry<String, ProductStats> entry : sortedByMarge) {
            cumulCA += entry.getValue().marge;
            double pourcentage = totalCA > 0 ? (cumulCA / totalCA) * 100 : 0.0;
            
            if (pourcentage <= 80) {
                produitsA.add(entry.getKey());
            } else if (pourcentage <= 95) {
                produitsB.add(entry.getKey());
            } else {
                produitsC.add(entry.getKey());
            }
        }
        
        List<DashboardKpiResponse.ProductABC> analyseABC = Arrays.asList(
                DashboardKpiResponse.ProductABC.builder()
                        .categorie("A")
                        .pourcentageCA(80.0)
                        .nombreProduits(produitsA.size())
                        .produits(produitsA.stream().limit(10).collect(Collectors.toList()))
                        .build(),
                DashboardKpiResponse.ProductABC.builder()
                        .categorie("B")
                        .pourcentageCA(15.0)
                        .nombreProduits(produitsB.size())
                        .produits(produitsB.stream().limit(10).collect(Collectors.toList()))
                        .build(),
                DashboardKpiResponse.ProductABC.builder()
                        .categorie("C")
                        .pourcentageCA(5.0)
                        .nombreProduits(produitsC.size())
                        .produits(produitsC.stream().limit(10).collect(Collectors.toList()))
                        .build()
        );
        
        // Taux de rotation moyen (simplifié: volume vendu / stock moyen)
        List<Double> tauxRotations = new ArrayList<>();
        for (Product p : products) {
            if (p.getQuantiteEnStock() != null && p.getQuantiteEnStock() > 0) {
                ProductStats stats = productStatsMap.get(p.getRefArticle());
                if (stats != null && stats.volume > 0) {
                    tauxRotations.add(stats.volume / p.getQuantiteEnStock());
                }
            }
        }
        double tauxRotationMoyen = tauxRotations.isEmpty() ? 0.0 :
                tauxRotations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Alertes stock
        List<DashboardKpiResponse.ProductAlert> alertesStock = new ArrayList<>();
        for (Product p : products) {
            if (p.getQuantiteEnStock() == null || p.getQuantiteEnStock() == 0) {
                alertesStock.add(DashboardKpiResponse.ProductAlert.builder()
                        .produitId(p.getId())
                        .produitNom(p.getDesignation() != null ? p.getDesignation() : p.getRefArticle())
                        .type("STOCK_EPUISE")
                        .message("Stock épuisé")
                        .build());
            } else if (p.getQuantiteEnStock() < 10) {
                alertesStock.add(DashboardKpiResponse.ProductAlert.builder()
                        .produitId(p.getId())
                        .produitNom(p.getDesignation() != null ? p.getDesignation() : p.getRefArticle())
                        .type("STOCK_FAIBLE")
                        .message("Stock faible: " + p.getQuantiteEnStock())
                        .build());
            }
        }
        
        // Produits sans vente
        Set<String> produitsAvecVente = productStatsMap.keySet();
        for (Product p : products) {
            if (!produitsAvecVente.contains(p.getRefArticle())) {
                alertesStock.add(DashboardKpiResponse.ProductAlert.builder()
                        .produitId(p.getId())
                        .produitNom(p.getDesignation() != null ? p.getDesignation() : p.getRefArticle())
                        .type("PAS_DE_VENTE")
                        .message("Aucune vente enregistrée")
                        .build());
            }
        }
        
        return DashboardKpiResponse.ProductPerformance.builder()
                .top10ParMarge(top10ParMarge)
                .top10ParVolume(top10ParVolume)
                .analyseABC(analyseABC)
                .tauxRotationMoyen(tauxRotationMoyen)
                .alertesStock(alertesStock)
                .build();
    }
    
    private static class ProductStats {
        double volume = 0.0;
        double volumeAchat = 0.0;
        double marge = 0.0;
        double margeUnitaire = 0.0;
        double margePourcentage = 0.0;
        String designation;
    }
}

