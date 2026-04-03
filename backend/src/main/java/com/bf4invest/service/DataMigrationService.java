package com.bf4invest.service;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.LigneAchat;
import com.bf4invest.model.LineItem;
import com.bf4invest.model.Product;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service pour gérer les migrations de données
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {
    
    private final BandeCommandeRepository bcRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final ProductRepository productRepository;
    private final ProductPriceService productPriceService;
    
    /**
     * Synchronise les références BC pour toutes les factures
     * Corrige les factures qui ont un bandeCommandeId mais pas de bcReference
     * 
     * @return Map avec les statistiques de migration
     */
    public Map<String, Integer> synchroniserReferencesBC() {
        log.info("🔄 Démarrage de la synchronisation des références BC pour les factures...");
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("facturesAchatMisesAJour", 0);
        stats.put("facturesVenteMisesAJour", 0);
        stats.put("erreursFacturesAchat", 0);
        stats.put("erreursFacturesVente", 0);
        
        // Créer une map ID BC -> Numéro BC pour recherche rapide
        Map<String, String> bcIdToNumeroMap = new HashMap<>();
        List<BandeCommande> allBCs = bcRepository.findAll();
        for (BandeCommande bc : allBCs) {
            if (bc.getId() != null && bc.getNumeroBC() != null) {
                bcIdToNumeroMap.put(bc.getId(), bc.getNumeroBC());
            }
        }
        
        log.info("📋 {} BCs trouvés dans la base", allBCs.size());
        
        // Mettre à jour les factures achat
        List<FactureAchat> facturesAchat = factureAchatRepository.findAll();
        log.info("🔍 Vérification de {} factures achat...", facturesAchat.size());
        
        for (FactureAchat fa : facturesAchat) {
            try {
                boolean needsUpdate = false;
                
                // Si la facture a un bandeCommandeId mais pas de bcReference
                if (fa.getBandeCommandeId() != null && 
                    (fa.getBcReference() == null || fa.getBcReference().isEmpty())) {
                    
                    String bcNumero = bcIdToNumeroMap.get(fa.getBandeCommandeId());
                    if (bcNumero != null) {
                        fa.setBcReference(bcNumero);
                        needsUpdate = true;
                        log.debug("✅ Facture achat {} : bcReference mis à jour avec {}", 
                                fa.getNumeroFactureAchat(), bcNumero);
                    } else {
                        log.warn("⚠️ Facture achat {} : BC ID {} trouvé mais numéro BC introuvable", 
                                fa.getNumeroFactureAchat(), fa.getBandeCommandeId());
                    }
                }
                
                if (needsUpdate) {
                    factureAchatRepository.save(fa);
                    stats.put("facturesAchatMisesAJour", stats.get("facturesAchatMisesAJour") + 1);
                }
                
            } catch (Exception e) {
                log.error("❌ Erreur lors de la mise à jour de la facture achat {}: {}", 
                        fa.getId(), e.getMessage());
                stats.put("erreursFacturesAchat", stats.get("erreursFacturesAchat") + 1);
            }
        }
        
        // Mettre à jour les factures vente
        List<FactureVente> facturesVente = factureVenteRepository.findAll();
        log.info("🔍 Vérification de {} factures vente...", facturesVente.size());
        
        for (FactureVente fv : facturesVente) {
            try {
                boolean needsUpdate = false;
                
                // Si la facture a un bandeCommandeId mais pas de bcReference
                if (fv.getBandeCommandeId() != null && 
                    (fv.getBcReference() == null || fv.getBcReference().isEmpty())) {
                    
                    String bcNumero = bcIdToNumeroMap.get(fv.getBandeCommandeId());
                    if (bcNumero != null) {
                        fv.setBcReference(bcNumero);
                        needsUpdate = true;
                        log.debug("✅ Facture vente {} : bcReference mis à jour avec {}", 
                                fv.getNumeroFactureVente(), bcNumero);
                    } else {
                        log.warn("⚠️ Facture vente {} : BC ID {} trouvé mais numéro BC introuvable", 
                                fv.getNumeroFactureVente(), fv.getBandeCommandeId());
                    }
                }
                
                if (needsUpdate) {
                    factureVenteRepository.save(fv);
                    stats.put("facturesVenteMisesAJour", stats.get("facturesVenteMisesAJour") + 1);
                }
                
            } catch (Exception e) {
                log.error("❌ Erreur lors de la mise à jour de la facture vente {}: {}", 
                        fv.getId(), e.getMessage());
                stats.put("erreursFacturesVente", stats.get("erreursFacturesVente") + 1);
            }
        }
        
        log.info("✅ Migration terminée :");
        log.info("   - {} factures achat mises à jour", stats.get("facturesAchatMisesAJour"));
        log.info("   - {} factures vente mises à jour", stats.get("facturesVenteMisesAJour"));
        log.info("   - {} erreurs factures achat", stats.get("erreursFacturesAchat"));
        log.info("   - {} erreurs factures vente", stats.get("erreursFacturesVente"));
        
        return stats;
    }
    
    /**
     * Migre les BCs de l'ancienne structure (lignes) vers la nouvelle structure (lignesAchat)
     * Convertit toutes les BCs qui ont seulement des lignes sans lignesAchat
     * 
     * @return Map avec les statistiques de migration
     */
    public Map<String, Integer> migrateBC_LignesToLignesAchat() {
        log.info("🔄 Démarrage de la migration lignes -> lignesAchat pour les BCs...");
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("bcsTraitees", 0);
        stats.put("bcsMisesAJour", 0);
        stats.put("erreurs", 0);
        
        List<BandeCommande> allBCs = bcRepository.findAll();
        log.info("📋 {} BCs trouvées dans la base", allBCs.size());
        
        for (BandeCommande bc : allBCs) {
            try {
                boolean needsUpdate = false;
                
                // Vérifier si la BC a des lignes mais pas de lignesAchat
                if ((bc.getLignesAchat() == null || bc.getLignesAchat().isEmpty()) 
                    && bc.getLignes() != null && !bc.getLignes().isEmpty()) {
                    
                    // Convertir chaque LineItem en LigneAchat
                    List<LigneAchat> lignesAchat = new ArrayList<>();
                    for (LineItem ligne : bc.getLignes()) {
                        LigneAchat ligneAchat = LigneAchat.builder()
                                .produitRef(ligne.getProduitRef())
                                .designation(ligne.getDesignation())
                                .unite(ligne.getUnite() != null ? ligne.getUnite() : "U")
                                .quantiteAchetee(ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee().doubleValue() : 0.0)
                                .prixAchatUnitaireHT(ligne.getPrixAchatUnitaireHT())
                                .tva(ligne.getTva())
                                .build();
                        
                        // Calculer les totaux pour cette ligne
                        if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getPrixAchatUnitaireHT() != null) {
                            ligneAchat.setTotalHT(ligneAchat.getQuantiteAchetee() * ligneAchat.getPrixAchatUnitaireHT());
                            if (ligneAchat.getTva() != null) {
                                ligneAchat.setTotalTTC(ligneAchat.getTotalHT() * (1 + (ligneAchat.getTva() / 100.0)));
                            }
                        }
                        
                        lignesAchat.add(ligneAchat);
                    }
                    
                    bc.setLignesAchat(lignesAchat);
                    needsUpdate = true;
                    log.debug("✅ BC {} : {} lignes converties en lignesAchat", 
                            bc.getNumeroBC(), lignesAchat.size());
                }
                
                if (needsUpdate) {
                    bc.setUpdatedAt(LocalDateTime.now());
                    bcRepository.save(bc);
                    stats.put("bcsMisesAJour", stats.get("bcsMisesAJour") + 1);
                }
                
                stats.put("bcsTraitees", stats.get("bcsTraitees") + 1);
                
            } catch (Exception e) {
                log.error("❌ Erreur lors de la migration de la BC {}: {}", 
                        bc.getId(), e.getMessage(), e);
                stats.put("erreurs", stats.get("erreurs") + 1);
            }
        }
        
        log.info("✅ Migration lignes -> lignesAchat terminée :");
        log.info("   - {} BCs traitées", stats.get("bcsTraitees"));
        log.info("   - {} BCs mises à jour", stats.get("bcsMisesAJour"));
        log.info("   - {} erreurs", stats.get("erreurs"));
        
        return stats;
    }
    
    /**
     * Migre les prix unitaires vers les prix pondérés pour tous les produits.
     * 
     * 1. Copie les prix unitaires vers les prix pondérés (pour rétrocompatibilité)
     * 2. Recalcule les prix pondérés depuis toutes les BC existantes
     * 
     * @return Map avec les statistiques de migration
     */
    public Map<String, Integer> migrateProductPricesToWeighted() {
        log.info("🔄 Démarrage de la migration des prix unitaires vers prix pondérés...");
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("produitsTraites", 0);
        stats.put("produitsMisesAJour", 0);
        stats.put("prixAchatCopies", 0);
        stats.put("prixVenteCopies", 0);
        stats.put("prixRecalcules", 0);
        stats.put("erreurs", 0);
        
        List<Product> allProducts = productRepository.findAll();
        log.info("   - {} produits à traiter", allProducts.size());
        
        for (Product product : allProducts) {
            try {
                // Étape 1: Copier les prix unitaires vers les prix pondérés si les prix pondérés sont null
                boolean step1Changed = false;
                if (product.getPrixAchatPondereHT() == null && product.getPrixAchatUnitaireHT() != null) {
                    product.setPrixAchatPondereHT(product.getPrixAchatUnitaireHT());
                    stats.put("prixAchatCopies", stats.get("prixAchatCopies") + 1);
                    step1Changed = true;
                }
                
                if (product.getPrixVentePondereHT() == null && product.getPrixVenteUnitaireHT() != null) {
                    product.setPrixVentePondereHT(product.getPrixVenteUnitaireHT());
                    stats.put("prixVenteCopies", stats.get("prixVenteCopies") + 1);
                    step1Changed = true;
                }
                
                // Persister l'étape 1 AVANT le recalcul : recalculate charge une copie fraîche en DB et sauvegarde
                // (min/max, pondérés). Un save(product) après avec cet objet stale écraserait ces champs.
                boolean migrationSaveDone = false;
                if (step1Changed) {
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);
                    migrationSaveDone = true;
                    stats.put("produitsMisesAJour", stats.get("produitsMisesAJour") + 1);
                }
                
                // Étape 2: Recalculer depuis les BC (sauvegarde déjà faite dans ProductPriceService)
                boolean recalcOk = false;
                try {
                    productPriceService.recalculateProductWeightedPrices(
                        product.getRefArticle(),
                        product.getDesignation(),
                        product.getUnite()
                    );
                    stats.put("prixRecalcules", stats.get("prixRecalcules") + 1);
                    recalcOk = true;
                } catch (Exception e) {
                    log.warn("⚠️  Impossible de recalculer les prix pour produit {}: {}", 
                        product.getRefArticle(), e.getMessage());
                    // Si l'étape 1 a été persistée, les prix copiés restent ; sinon inchangé
                }
                
                if (recalcOk && !migrationSaveDone) {
                    stats.put("produitsMisesAJour", stats.get("produitsMisesAJour") + 1);
                }
                
                stats.put("produitsTraites", stats.get("produitsTraites") + 1);
                
                // Log progression tous les 100 produits
                if (stats.get("produitsTraites") % 100 == 0) {
                    log.info("   - {} produits traités...", stats.get("produitsTraites"));
                }
                
            } catch (Exception e) {
                log.error("❌ Erreur lors de la migration du produit {}: {}", 
                    product.getId(), e.getMessage(), e);
                stats.put("erreurs", stats.get("erreurs") + 1);
            }
        }
        
        log.info("✅ Migration des prix terminée :");
        log.info("   - {} produits traités", stats.get("produitsTraites"));
        log.info("   - {} produits mis à jour", stats.get("produitsMisesAJour"));
        log.info("   - {} prix d'achat copiés", stats.get("prixAchatCopies"));
        log.info("   - {} prix de vente copiés", stats.get("prixVenteCopies"));
        log.info("   - {} prix recalculés depuis les BC", stats.get("prixRecalcules"));
        log.info("   - {} erreurs", stats.get("erreurs"));
        
        return stats;
    }
}

