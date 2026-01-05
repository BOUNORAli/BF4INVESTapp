package com.bf4invest.service;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.LigneAchat;
import com.bf4invest.model.LigneVente;
import com.bf4invest.model.Product;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service pour gérer le calcul des prix pondérés des produits.
 * Les prix pondérés sont calculés depuis toutes les BC contenant le produit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPriceService {
    
    private final ProductRepository productRepository;
    private final BandeCommandeRepository bcRepository;
    
    /**
     * Recalcule les prix pondérés d'un produit depuis toutes les BC.
     * 
     * @param productRef Référence article du produit
     * @param designation Désignation du produit
     * @param unite Unité du produit
     */
    public void recalculateProductWeightedPrices(String productRef, String designation, String unite) {
        // Normaliser les valeurs
        if (productRef == null) productRef = "";
        if (designation == null) designation = "";
        if (unite == null || unite.isEmpty()) unite = "U";
        
        // Trouver le produit
        Optional<Product> productOpt = productRepository.findByRefArticleAndDesignationAndUnite(
            productRef, designation, unite);
        
        if (!productOpt.isPresent()) {
            log.warn("Produit non trouvé pour recalcul: ref={}, designation={}, unite={}", 
                productRef, designation, unite);
            return;
        }
        
        Product product = productOpt.get();
        
        // Trouver toutes les BC contenant ce produit
        List<BandeCommande> allBCs = bcRepository.findAll();
        
        double sommePrixAchatPondere = 0.0;
        double quantiteAcheteeTotale = 0.0;
        double sommePrixVentePondere = 0.0;
        double quantiteVendueTotale = 0.0;
        Double tva = null;
        
        // Parcourir toutes les BC
        for (BandeCommande bc : allBCs) {
            if (bc.getLignesAchat() == null) {
                continue;
            }
            
            // Chercher ce produit dans les lignes d'achat
            for (LigneAchat ligneAchat : bc.getLignesAchat()) {
                if (matchesProduct(ligneAchat, productRef, designation, unite)) {
                    // Agréger les données d'achat
                    if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getQuantiteAchetee() > 0 &&
                        ligneAchat.getPrixAchatUnitaireHT() != null && ligneAchat.getPrixAchatUnitaireHT() > 0) {
                        double qty = ligneAchat.getQuantiteAchetee();
                        double prix = ligneAchat.getPrixAchatUnitaireHT();
                        quantiteAcheteeTotale += qty;
                        sommePrixAchatPondere += prix * qty;
                    }
                    
                    if (ligneAchat.getTva() != null && tva == null) {
                        tva = ligneAchat.getTva();
                    }
                }
            }
            
            // Chercher ce produit dans les lignes de vente (tous clients confondus)
            if (bc.getClientsVente() != null) {
                for (var clientVente : bc.getClientsVente()) {
                    if (clientVente.getLignesVente() != null) {
                        for (LigneVente ligneVente : clientVente.getLignesVente()) {
                            if (matchesProduct(ligneVente, productRef, designation, unite)) {
                                // Agréger les données de vente
                                if (ligneVente.getQuantiteVendue() != null && ligneVente.getQuantiteVendue() > 0 &&
                                    ligneVente.getPrixVenteUnitaireHT() != null && ligneVente.getPrixVenteUnitaireHT() > 0) {
                                    double qty = ligneVente.getQuantiteVendue();
                                    double prix = ligneVente.getPrixVenteUnitaireHT();
                                    quantiteVendueTotale += qty;
                                    sommePrixVentePondere += prix * qty;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Calculer les prix pondérés globaux
        Double prixAchatPondere = quantiteAcheteeTotale > 0 
            ? sommePrixAchatPondere / quantiteAcheteeTotale 
            : null;
        Double prixVentePondere = quantiteVendueTotale > 0
            ? sommePrixVentePondere / quantiteVendueTotale
            : null;
        
        // Mettre à jour le produit
        product.setPrixAchatPondereHT(prixAchatPondere);
        product.setPrixVentePondereHT(prixVentePondere);
        if (tva != null) {
            product.setTva(tva);
        }
        product.setDerniereMiseAJourPrix(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        
        productRepository.save(product);
        
        log.info("Prix pondérés recalculés pour produit {}: Achat={}, Vente={}", 
            product.getRefArticle(), prixAchatPondere, prixVentePondere);
    }
    
    /**
     * Vérifie si une ligne d'achat correspond au produit recherché
     */
    private boolean matchesProduct(LigneAchat ligne, String productRef, String designation, String unite) {
        boolean refMatch = (ligne.getProduitRef() == null && productRef == null) ||
                          (ligne.getProduitRef() != null && ligne.getProduitRef().equals(productRef));
        boolean designationMatch = (ligne.getDesignation() == null && designation == null) ||
                                  (ligne.getDesignation() != null && ligne.getDesignation().equals(designation));
        String ligneUnite = ligne.getUnite() != null ? ligne.getUnite() : "U";
        String searchUnite = unite != null && !unite.isEmpty() ? unite : "U";
        boolean uniteMatch = ligneUnite.equals(searchUnite);
        
        return refMatch && designationMatch && uniteMatch;
    }
    
    /**
     * Vérifie si une ligne de vente correspond au produit recherché
     */
    private boolean matchesProduct(LigneVente ligne, String productRef, String designation, String unite) {
        boolean refMatch = (ligne.getProduitRef() == null && productRef == null) ||
                          (ligne.getProduitRef() != null && ligne.getProduitRef().equals(productRef));
        boolean designationMatch = (ligne.getDesignation() == null && designation == null) ||
                                  (ligne.getDesignation() != null && ligne.getDesignation().equals(designation));
        String ligneUnite = ligne.getUnite() != null ? ligne.getUnite() : "U";
        String searchUnite = unite != null && !unite.isEmpty() ? unite : "U";
        boolean uniteMatch = ligneUnite.equals(searchUnite);
        
        return refMatch && designationMatch && uniteMatch;
    }
    
    /**
     * Recalcule les prix pondérés pour tous les produits.
     * Utile après une migration ou un import massif.
     */
    public void recalculateAllProductPrices() {
        log.info("Démarrage du recalcul des prix pondérés pour tous les produits...");
        List<Product> allProducts = productRepository.findAll();
        int count = 0;
        
        for (Product product : allProducts) {
            try {
                recalculateProductWeightedPrices(
                    product.getRefArticle(),
                    product.getDesignation(),
                    product.getUnite()
                );
                count++;
                
                if (count % 100 == 0) {
                    log.info("Recalculé {} produits sur {}", count, allProducts.size());
                }
            } catch (Exception e) {
                log.error("Erreur lors du recalcul pour produit {}: {}", 
                    product.getRefArticle(), e.getMessage(), e);
            }
        }
        
        log.info("Recalcul terminé: {} produits traités", count);
    }
}

