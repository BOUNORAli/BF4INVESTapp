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
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final BandeCommandeRepository bcRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    // Nouveaux services d'analyse
    private final ChargeAnalysisService chargeAnalysisService;
    private final PaymentAnalysisService paymentAnalysisService;
    private final ProductPerformanceService productPerformanceService;
    private final TreasuryForecastService treasuryForecastService;
    private final BCAnalysisService bcAnalysisService;
    private final BalanceHistoryService balanceHistoryService;
    
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
        // IMPORTANT: Exclure les avoirs (montants négatifs) du calcul
        double caHTFromInvoices = facturesVente.stream()
                .filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()) // Exclure les avoirs
                .mapToDouble(f -> {
                    double totalHT = f.getTotalHT() != null ? f.getTotalHT() : 0.0;
                    // S'assurer que les montants sont positifs (les avoirs peuvent avoir des montants négatifs)
                    return Math.max(0.0, totalHT);
                })
                .sum();
        
        // Calculer aussi le total TTC pour comparaison
        double caTTCFromInvoices = facturesVente.stream()
                .filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()) // Exclure les avoirs
                .mapToDouble(f -> {
                    double totalTTC = f.getTotalTTC() != null ? f.getTotalTTC() : 0.0;
                    return Math.max(0.0, totalTTC);
                })
                .sum();
        
        // Identifier les BCs non facturées pour logging
        Set<String> bcIdsAvecFactures = facturesVente.stream()
                .filter(f -> f.getBandeCommandeId() != null && !f.getBandeCommandeId().isEmpty())
                .map(FactureVente::getBandeCommandeId)
                .collect(Collectors.toSet());
        
        long nbBCsNonFacturees = allBCs.stream()
                .filter(bc -> !bcIdsAvecFactures.contains(bc.getId()))
                .count();
        
        // Log des incohérences importantes pour diagnostic
        if (caHTFromBCs > 0 && caHTFromInvoices > 0) {
            double differenceAbsolue = Math.abs(caHTFromBCs - caHTFromInvoices);
            double differencePourcentage = (differenceAbsolue / Math.max(caHTFromBCs, caHTFromInvoices)) * 100;
            
            if (differencePourcentage > 10) { // Alerte si différence > 10%
                log.warn("⚠️ INCOHÉRENCE DÉTECTÉE: Différence significative entre CA BCs et Factures Vente");
                log.warn("   - Total Vente HT depuis BCs: {} MAD", NumberUtils.roundTo2Decimals(caHTFromBCs));
                log.warn("   - Total Vente HT depuis Factures: {} MAD", NumberUtils.roundTo2Decimals(caHTFromInvoices));
                log.warn("   - Différence: {} MAD ({}%)", NumberUtils.roundTo2Decimals(differenceAbsolue), 
                    String.format("%.2f", differencePourcentage));
                log.warn("   - Nombre de BCs non facturées: {}", nbBCsNonFacturees);
                log.warn("   - Nombre total de BCs: {}", allBCs.size());
                log.warn("   - Nombre de factures vente (hors avoirs): {}", 
                    facturesVente.stream().filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()).count());
                
                // Afficher les avoirs si présents
                long nbAvoirs = facturesVente.stream()
                        .filter(f -> Boolean.TRUE.equals(f.getEstAvoir()))
                        .count();
                if (nbAvoirs > 0) {
                    double totalAvoirs = facturesVente.stream()
                            .filter(f -> Boolean.TRUE.equals(f.getEstAvoir()))
                            .mapToDouble(f -> Math.abs(f.getTotalHT() != null ? f.getTotalHT() : 0.0))
                            .sum();
                    log.warn("   - Nombre d'avoirs vente: {} (Total: {} MAD)", nbAvoirs, 
                        NumberUtils.roundTo2Decimals(totalAvoirs));
                }
            } else {
                log.info("✓ Cohérence vérifiée: CA BCs ({}) vs Factures Vente ({}) - Différence: {}%", 
                    NumberUtils.roundTo2Decimals(caHTFromBCs), NumberUtils.roundTo2Decimals(caHTFromInvoices),
                    String.format("%.2f", differencePourcentage));
            }
        } else if (caHTFromBCs > 0 || caHTFromInvoices > 0) {
            // Si une seule source a des données, log info
            log.info("CA calculé depuis {} - BCs: {} MAD, Factures: {} MAD", 
                caHTFromBCs > 0 ? "BCs" : "Factures", 
                NumberUtils.roundTo2Decimals(caHTFromBCs), 
                NumberUtils.roundTo2Decimals(caHTFromInvoices));
        }
        
        // Utiliser la valeur la plus élevée ou celle des BCs si disponible
        double caHT = NumberUtils.roundTo2Decimals(caHTFromBCs > 0 ? caHTFromBCs : caHTFromInvoices);
        
        // CA TTC depuis factures (exclure les avoirs)
        double caTTC = NumberUtils.roundTo2Decimals(facturesVente.stream()
                .filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()) // Exclure les avoirs
                .mapToDouble(f -> {
                    double totalTTC = f.getTotalTTC() != null ? f.getTotalTTC() : 0.0;
                    return Math.max(0.0, totalTTC); // S'assurer que c'est positif
                })
                .sum());
        
        // Achats - Exclure les avoirs (montants négatifs)
        double totalAchatsHT = NumberUtils.roundTo2Decimals(facturesAchat.stream()
                .filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()) // Exclure les avoirs
                .mapToDouble(f -> {
                    double totalHT = f.getTotalHT() != null ? f.getTotalHT() : 0.0;
                    return Math.max(0.0, totalHT); // S'assurer que c'est positif
                })
                .sum());
        
        double totalAchatsTTC = NumberUtils.roundTo2Decimals(facturesAchat.stream()
                .filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()) // Exclure les avoirs
                .mapToDouble(f -> {
                    double totalTTC = f.getTotalTTC() != null ? f.getTotalTTC() : 0.0;
                    return Math.max(0.0, totalTTC); // S'assurer que c'est positif
                })
                .sum());
        
        // Vérifier incohérences pour achats
        double totalAchatHTFromBCsAll = NumberUtils.roundTo2Decimals(allBCs.stream()
                .mapToDouble(bc -> bc.getTotalAchatHT() != null ? bc.getTotalAchatHT() : 0.0)
                .sum());
        
        if (totalAchatHTFromBCsAll > 0 && totalAchatsHT > 0) {
            double diffAchat = Math.abs(totalAchatHTFromBCsAll - totalAchatsHT);
            double diffPctAchat = (diffAchat / Math.max(totalAchatHTFromBCsAll, totalAchatsHT)) * 100;
            
            if (diffPctAchat > 10) {
                log.warn("⚠️ INCOHÉRENCE DÉTECTÉE: Différence significative entre Achats BCs et Factures Achat");
                log.warn("   - Total Achat HT depuis BCs: {} MAD", NumberUtils.roundTo2Decimals(totalAchatHTFromBCsAll));
                log.warn("   - Total Achat HT depuis Factures: {} MAD", NumberUtils.roundTo2Decimals(totalAchatsHT));
                log.warn("   - Différence: {} MAD ({}%)", NumberUtils.roundTo2Decimals(diffAchat), 
                    String.format("%.2f", diffPctAchat));
            }
        }
        
        // Marges - Calculer à partir des BCs pour une meilleure précision
        // Les BCs contiennent la source de vérité pour les prix d'achat et de vente
        List<BandeCommande> bcs = bcRepository.findAll();
        double totalAchatHTFromBCs = NumberUtils.roundTo2Decimals(bcs.stream()
                .mapToDouble(bc -> bc.getTotalAchatHT() != null ? bc.getTotalAchatHT() : 0.0)
                .sum());
        double totalVenteHTFromBCs = NumberUtils.roundTo2Decimals(bcs.stream()
                .mapToDouble(bc -> bc.getTotalVenteHT() != null ? bc.getTotalVenteHT() : 0.0)
                .sum());
        
        // Utiliser les BCs pour calculer la marge (plus précis)
        // Sinon utiliser les factures comme fallback
        double margeTotale;
        double totalAchatsHTForMargin;
        
        if (totalVenteHTFromBCs > 0) {
            // Utiliser les BCs comme source principale
            margeTotale = NumberUtils.roundTo2Decimals(totalVenteHTFromBCs - totalAchatHTFromBCs);
            totalAchatsHTForMargin = totalAchatHTFromBCs;
        } else {
            // Fallback sur les factures si pas de BCs
            margeTotale = NumberUtils.roundTo2Decimals(caHT - totalAchatsHT);
            totalAchatsHTForMargin = totalAchatsHT;
        }
        
        double margeMoyenne = NumberUtils.roundTo2Decimals(totalAchatsHTForMargin > 0 ? (margeTotale / totalAchatsHTForMargin) * 100 : 0.0);
        
        // TVA - Exclure les avoirs (ils réduisent la TVA, donc on les soustrait plutôt que de les exclure complètement)
        // Pour avoirs: TVA est négative, donc l'addition la réduit automatiquement
        double tvaCollectee = NumberUtils.roundTo2Decimals(facturesVente.stream()
                .mapToDouble(f -> {
                    double tva = f.getTotalTVA() != null ? f.getTotalTVA() : 0.0;
                    // Pour les avoirs, la TVA est déjà négative, donc l'addition fonctionne correctement
                    return tva;
                })
                .sum());
        double tvaDeductible = NumberUtils.roundTo2Decimals(facturesAchat.stream()
                .mapToDouble(f -> {
                    double tva = f.getTotalTVA() != null ? f.getTotalTVA() : 0.0;
                    // Pour les avoirs, la TVA est déjà négative, donc l'addition fonctionne correctement
                    return tva;
                })
                .sum());
        
        // Impayés
        DashboardKpiResponse.ImpayesInfo impayes = calculateImpayes(facturesAchat, facturesVente);
        
        // Factures en retard - Exclure les avoirs
        long facturesEnRetard = factureAchatRepository.findByDateEcheanceLessThanEqual(LocalDate.now())
                .stream()
                .filter(f -> !"regle".equals(f.getEtatPaiement()))
                .filter(f -> f.getEstAvoir() == null || !f.getEstAvoir()) // Exclure les avoirs
                .count();
        
        // CA Mensuel
        List<DashboardKpiResponse.MonthlyData> caMensuel = calculateCaMensuel(facturesVente, from, to);
        
        // Top Fournisseurs et Clients
        List<DashboardKpiResponse.FournisseurClientStat> topFournisseurs = calculateTopFournisseurs(facturesAchat);
        List<DashboardKpiResponse.FournisseurClientStat> topClients = calculateTopClients(facturesVente);
        
        // Nouvelles analyses
        DashboardKpiResponse.ChargeAnalysis chargeAnalysis = chargeAnalysisService.analyzeCharges(from, to);
        DashboardKpiResponse.PaymentAnalysis paymentAnalysis = paymentAnalysisService.analyzePayments(from, to);
        DashboardKpiResponse.ProductPerformance productPerformance = productPerformanceService.analyzeProducts(from, to);
        DashboardKpiResponse.TreasuryForecast treasuryForecast = treasuryForecastService.generateForecast(from, to);
        DashboardKpiResponse.BCAnalysis bcAnalysis = bcAnalysisService.analyzeBCs(from, to);
        DashboardKpiResponse.BalanceHistory balanceHistory = balanceHistoryService.getBalanceHistory(from, to);
        
        // Graphiques avancés (calculs basés sur les données existantes)
        DashboardKpiResponse.AdvancedCharts advancedCharts = calculateAdvancedCharts(caMensuel, margeTotale, margeMoyenne, from, to);
        
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
                .chargeAnalysis(chargeAnalysis)
                .paymentAnalysis(paymentAnalysis)
                .productPerformance(productPerformance)
                .treasuryForecast(treasuryForecast)
                .bcAnalysis(bcAnalysis)
                .balanceHistory(balanceHistory)
                .advancedCharts(advancedCharts)
                .build();
    }
    
    private DashboardKpiResponse.AdvancedCharts calculateAdvancedCharts(
            List<DashboardKpiResponse.MonthlyData> caMensuel,
            double margeTotale,
            double margeMoyenne,
            LocalDate from,
            LocalDate to) {
        
        // Evolution des marges
        List<DashboardKpiResponse.MarginEvolution> evolutionMarges = caMensuel.stream()
                .map(m -> DashboardKpiResponse.MarginEvolution.builder()
                        .mois(m.getMois())
                        .marge(m.getMarge())
                        .margePourcentage(m.getMarge())
                        .build())
                .collect(Collectors.toList());
        
        // Corrélation CA vs Charges (simplifié - utiliser les charges totales)
        DashboardKpiResponse.ChargeAnalysis charges = chargeAnalysisService.analyzeCharges(from, to);
        double totalCharges = charges != null ? charges.getTotalCharges() : 0.0;
        
        List<DashboardKpiResponse.CAChargeCorrelation> correlationCACharges = caMensuel.stream()
                .map(m -> {
                    double chargesMensuelles = NumberUtils.roundTo2Decimals(totalCharges / Math.max(caMensuel.size(), 1));
                    double ratio = NumberUtils.roundTo2Decimals(m.getCaHT() > 0 ? chargesMensuelles / m.getCaHT() : 0.0);
                    return DashboardKpiResponse.CAChargeCorrelation.builder()
                            .mois(m.getMois())
                            .ca(m.getCaHT())
                            .charges(chargesMensuelles)
                            .ratio(ratio)
                            .build();
                })
                .collect(Collectors.toList());
        
        // Indicateurs de croissance
        double croissanceMoM = 0.0;
        double croissanceYoY = 0.0;
        if (caMensuel.size() >= 2) {
            List<DashboardKpiResponse.MonthlyData> sorted = caMensuel.stream()
                    .sorted(Comparator.comparing(DashboardKpiResponse.MonthlyData::getMois))
                    .collect(Collectors.toList());
            double caMoisActuel = sorted.get(sorted.size() - 1).getCaHT();
            double caMoisPrecedent = sorted.get(sorted.size() - 2).getCaHT();
            if (caMoisPrecedent > 0) {
                croissanceMoM = NumberUtils.roundTo2Decimals(((caMoisActuel - caMoisPrecedent) / caMoisPrecedent) * 100);
            }
        }
        
        DashboardKpiResponse.GrowthIndicators indicateursCroissance = DashboardKpiResponse.GrowthIndicators.builder()
                .croissanceMoM(croissanceMoM)
                .croissanceYoY(NumberUtils.roundTo2Decimals(croissanceYoY)) // Simplifié - nécessiterait données année précédente
                .croissanceMoyenne(croissanceMoM)
                .build();
        
        // Heatmap mensuelle (score composite)
        List<DashboardKpiResponse.MonthlyPerformance> heatmapMensuelle = caMensuel.stream()
                .map(m -> {
                    double score = 0.0;
                    String niveau = "FAIBLE";
                    
                    // Score basé sur CA et marge
                    if (m.getCaHT() > 0 && m.getMarge() > 0) {
                        score = NumberUtils.roundTo2Decimals((m.getCaHT() / 100000.0) * 50 + (m.getMarge() / 20.0) * 50);
                        score = Math.min(score, 100.0);
                        
                        if (score >= 80) niveau = "EXCELLENT";
                        else if (score >= 60) niveau = "BON";
                        else if (score >= 40) niveau = "MOYEN";
                    }
                    
                    return DashboardKpiResponse.MonthlyPerformance.builder()
                            .mois(m.getMois())
                            .score(NumberUtils.roundTo2Decimals(score))
                            .niveau(niveau)
                            .build();
                })
                .collect(Collectors.toList());
        
        return DashboardKpiResponse.AdvancedCharts.builder()
                .evolutionMarges(evolutionMarges)
                .correlationCACharges(correlationCACharges)
                .indicateursCroissance(indicateursCroissance)
                .heatmapMensuelle(heatmapMensuelle)
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
        
        // Factures achat impayées - Exclure les avoirs
        for (FactureAchat fa : facturesAchat) {
            // Exclure les avoirs (ils ne peuvent pas être impayés, ce sont des crédits)
            if (Boolean.TRUE.equals(fa.getEstAvoir())) {
                continue;
            }
            if (!"regle".equals(fa.getEtatPaiement()) && fa.getDateEcheance() != null) {
                double montant = fa.getMontantRestant() != null ? fa.getMontantRestant() : fa.getTotalTTC();
                // S'assurer que le montant est positif
                montant = Math.max(0.0, montant);
                if (montant > 0) {
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
        }
        
        // Factures vente impayées - Exclure les avoirs
        for (FactureVente fv : facturesVente) {
            // Exclure les avoirs (ils ne peuvent pas être impayés, ce sont des crédits)
            if (Boolean.TRUE.equals(fv.getEstAvoir())) {
                continue;
            }
            if (!"regle".equals(fv.getEtatPaiement()) && fv.getDateEcheance() != null) {
                double montant = fv.getMontantRestant() != null ? fv.getMontantRestant() : fv.getTotalTTC();
                // S'assurer que le montant est positif
                montant = Math.max(0.0, montant);
                if (montant > 0) {
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
        }
        
        return DashboardKpiResponse.ImpayesInfo.builder()
                .totalImpayes(NumberUtils.roundTo2Decimals(impayes0_30 + impayes31_60 + impayesPlus60))
                .impayes0_30(NumberUtils.roundTo2Decimals(impayes0_30))
                .impayes31_60(NumberUtils.roundTo2Decimals(impayes31_60))
                .impayesPlus60(NumberUtils.roundTo2Decimals(impayesPlus60))
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
                    .caHT(NumberUtils.roundTo2Decimals(ca))
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


