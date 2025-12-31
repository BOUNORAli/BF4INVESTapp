package com.bf4invest.service;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.LigneAchat;
import com.bf4invest.model.LineItem;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
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
}

