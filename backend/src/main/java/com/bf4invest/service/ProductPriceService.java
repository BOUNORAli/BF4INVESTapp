package com.bf4invest.service;

import com.bf4invest.dto.ProductBcUsageDto;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.ClientVente;
import com.bf4invest.model.FournisseurAchat;
import com.bf4invest.model.LineItem;
import com.bf4invest.model.LigneAchat;
import com.bf4invest.model.LigneVente;
import com.bf4invest.model.Product;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
        
        Double prixAchatMin = null;
        Double prixAchatMax = null;
        Double prixVenteMin = null;
        Double prixVenteMax = null;
        
        // Parcourir toutes les BC
        for (BandeCommande bc : allBCs) {
            // --- ACHATS ---
            // Nouvelle structure: fournisseursAchat[].lignesAchat
            if (bc.getFournisseursAchat() != null && !bc.getFournisseursAchat().isEmpty()) {
                for (FournisseurAchat fa : bc.getFournisseursAchat()) {
                    if (fa == null || fa.getLignesAchat() == null) continue;
                    for (LigneAchat ligneAchat : fa.getLignesAchat()) {
                        if (matchesProduct(ligneAchat, productRef, designation, unite)) {
                            if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getQuantiteAchetee() > 0 &&
                                ligneAchat.getPrixAchatUnitaireHT() != null && ligneAchat.getPrixAchatUnitaireHT() > 0) {
                                double qty = ligneAchat.getQuantiteAchetee();
                                double prix = ligneAchat.getPrixAchatUnitaireHT();
                                quantiteAcheteeTotale += qty;
                                sommePrixAchatPondere += prix * qty;

                                prixAchatMin = prixAchatMin == null ? prix : Math.min(prixAchatMin, prix);
                                prixAchatMax = prixAchatMax == null ? prix : Math.max(prixAchatMax, prix);
                            }

                            if (ligneAchat.getTva() != null && tva == null) {
                                tva = ligneAchat.getTva();
                            }
                        }
                    }
                }
            } else if (bc.getLignesAchat() != null) {
                // Ancienne structure: bc.lignesAchat
                for (LigneAchat ligneAchat : bc.getLignesAchat()) {
                    if (matchesProduct(ligneAchat, productRef, designation, unite)) {
                        if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getQuantiteAchetee() > 0 &&
                            ligneAchat.getPrixAchatUnitaireHT() != null && ligneAchat.getPrixAchatUnitaireHT() > 0) {
                            double qty = ligneAchat.getQuantiteAchetee();
                            double prix = ligneAchat.getPrixAchatUnitaireHT();
                            quantiteAcheteeTotale += qty;
                            sommePrixAchatPondere += prix * qty;

                            prixAchatMin = prixAchatMin == null ? prix : Math.min(prixAchatMin, prix);
                            prixAchatMax = prixAchatMax == null ? prix : Math.max(prixAchatMax, prix);
                        }

                        if (ligneAchat.getTva() != null && tva == null) {
                            tva = ligneAchat.getTva();
                        }
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
                                    
                                    prixVenteMin = prixVenteMin == null ? prix : Math.min(prixVenteMin, prix);
                                    prixVenteMax = prixVenteMax == null ? prix : Math.max(prixVenteMax, prix);
                                }
                            }
                        }
                    }
                }
            }

            // --- LEGACY: bc.lignes (LineItem) — achat + vente dans le même objet (anciens BC)
            if (bc.getLignes() != null) {
                for (LineItem li : bc.getLignes()) {
                    if (li == null || !matchesProductLineItem(li, productRef, designation, unite)) {
                        continue;
                    }
                    if (li.getQuantiteAchetee() != null && li.getQuantiteAchetee() > 0
                        && li.getPrixAchatUnitaireHT() != null && li.getPrixAchatUnitaireHT() > 0) {
                        double qty = li.getQuantiteAchetee();
                        double prix = li.getPrixAchatUnitaireHT();
                        quantiteAcheteeTotale += qty;
                        sommePrixAchatPondere += prix * qty;
                        prixAchatMin = prixAchatMin == null ? prix : Math.min(prixAchatMin, prix);
                        prixAchatMax = prixAchatMax == null ? prix : Math.max(prixAchatMax, prix);
                    }
                    if (li.getQuantiteVendue() != null && li.getQuantiteVendue() > 0
                        && li.getPrixVenteUnitaireHT() != null && li.getPrixVenteUnitaireHT() > 0) {
                        double qty = li.getQuantiteVendue();
                        double prix = li.getPrixVenteUnitaireHT();
                        quantiteVendueTotale += qty;
                        sommePrixVentePondere += prix * qty;
                        prixVenteMin = prixVenteMin == null ? prix : Math.min(prixVenteMin, prix);
                        prixVenteMax = prixVenteMax == null ? prix : Math.max(prixVenteMax, prix);
                    }
                    if (li.getTva() != null && tva == null) {
                        tva = li.getTva();
                    }
                }
            }
        }
        
        // Calculer les prix pondérés globaux (arrondis à 2 décimales)
        Double prixAchatPondere = null;
        if (quantiteAcheteeTotale > 0) {
            double prix = sommePrixAchatPondere / quantiteAcheteeTotale;
            prixAchatPondere = BigDecimal.valueOf(prix)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        }
        Double prixVentePondere = null;
        if (quantiteVendueTotale > 0) {
            double prix = sommePrixVentePondere / quantiteVendueTotale;
            prixVentePondere = BigDecimal.valueOf(prix)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        }
        
        // Ne pas écraser avec null si aucune ligne BC ne matche (évite régression après backfill)
        boolean anyUpdate = false;
        if (quantiteAcheteeTotale > 0) {
            product.setPrixAchatPondereHT(prixAchatPondere);
            product.setPrixAchatMinHT(BigDecimal.valueOf(prixAchatMin).setScale(2, RoundingMode.HALF_UP).doubleValue());
            product.setPrixAchatMaxHT(BigDecimal.valueOf(prixAchatMax).setScale(2, RoundingMode.HALF_UP).doubleValue());
            anyUpdate = true;
        }
        if (quantiteVendueTotale > 0) {
            product.setPrixVentePondereHT(prixVentePondere);
            product.setPrixVenteMinHT(BigDecimal.valueOf(prixVenteMin).setScale(2, RoundingMode.HALF_UP).doubleValue());
            product.setPrixVenteMaxHT(BigDecimal.valueOf(prixVenteMax).setScale(2, RoundingMode.HALF_UP).doubleValue());
            anyUpdate = true;
        }
        if (tva != null) {
            product.setTva(tva);
            anyUpdate = true;
        }

        // Restaurer depuis prix unitaires si pondéré corrompu (null ou <= 0) et pas de données BC pour ce côté
        if (quantiteAcheteeTotale <= 0) {
            Double unit = product.getPrixAchatUnitaireHT();
            if (unit != null && unit > 0
                && (product.getPrixAchatPondereHT() == null || product.getPrixAchatPondereHT() <= 0)) {
                double r = round2(unit);
                product.setPrixAchatPondereHT(r);
                product.setPrixAchatMinHT(r);
                product.setPrixAchatMaxHT(r);
                anyUpdate = true;
            }
        }
        if (quantiteVendueTotale <= 0) {
            Double unit = product.getPrixVenteUnitaireHT();
            if (unit != null && unit > 0
                && (product.getPrixVentePondereHT() == null || product.getPrixVentePondereHT() <= 0)) {
                double r = round2(unit);
                product.setPrixVentePondereHT(r);
                product.setPrixVenteMinHT(r);
                product.setPrixVenteMaxHT(r);
                anyUpdate = true;
            }
        }

        if (!anyUpdate) {
            log.debug("Aucune ligne BC pour produit ref={} — prix existants conservés (pas de save)", product.getRefArticle());
            return;
        }

        product.setDerniereMiseAJourPrix(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Prix pondérés recalculés pour produit {}: Achat={}, Vente={}",
            product.getRefArticle(), product.getPrixAchatPondereHT(), product.getPrixVentePondereHT());
    }

    /**
     * Liste des bons de commande où le produit apparaît (toutes structures BC).
     */
    public List<ProductBcUsageDto> findBandeCommandesUsingProduct(String productId) {
        Optional<Product> opt = productRepository.findById(productId);
        if (!opt.isPresent()) {
            return List.of();
        }
        Product p = opt.get();
        String productRef = p.getRefArticle() != null ? p.getRefArticle() : "";
        String designation = p.getDesignation() != null ? p.getDesignation() : "";
        String unite = (p.getUnite() != null && !p.getUnite().isEmpty()) ? p.getUnite() : "U";

        List<ProductBcUsageDto> out = new ArrayList<>();
        for (BandeCommande bc : bcRepository.findAll()) {
            UsageAgg agg = new UsageAgg();
            accumulateProductUsageInBc(bc, productRef, designation, unite, agg);
            if (!agg.matched) {
                continue;
            }
            Double buyAvg = agg.qtyBuy > 0 ? round2(agg.sumBuy / agg.qtyBuy) : null;
            Double sellAvg = agg.qtySell > 0 ? round2(agg.sumSell / agg.qtySell) : null;
            out.add(ProductBcUsageDto.builder()
                .bandeCommandeId(bc.getId())
                .numeroBC(bc.getNumeroBC())
                .dateBC(bc.getDateBC())
                .quantiteAcheteeTotale(agg.qtyBuy > 0 ? round2(agg.qtyBuy) : null)
                .prixAchatUnitaireHtPondere(buyAvg)
                .quantiteVendueTotale(agg.qtySell > 0 ? round2(agg.qtySell) : null)
                .prixVenteUnitaireHtPondere(sellAvg)
                .fournisseurIds(agg.supplierIds.isEmpty() ? null : String.join(",", agg.supplierIds))
                .clientIds(agg.clientIds.isEmpty() ? null : String.join(",", agg.clientIds))
                .build());
        }
        out.sort(Comparator.comparing(ProductBcUsageDto::getDateBC, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private static final class UsageAgg {
        boolean matched;
        double qtyBuy;
        double sumBuy;
        double qtySell;
        double sumSell;
        final LinkedHashSet<String> supplierIds = new LinkedHashSet<>();
        final LinkedHashSet<String> clientIds = new LinkedHashSet<>();
    }

    private void accumulateProductUsageInBc(BandeCommande bc, String productRef, String designation, String unite, UsageAgg agg) {
        if (bc.getFournisseursAchat() != null && !bc.getFournisseursAchat().isEmpty()) {
            for (FournisseurAchat fa : bc.getFournisseursAchat()) {
                if (fa == null || fa.getLignesAchat() == null) {
                    continue;
                }
                for (LigneAchat ligneAchat : fa.getLignesAchat()) {
                    if (!matchesProduct(ligneAchat, productRef, designation, unite)) {
                        continue;
                    }
                    agg.matched = true;
                    if (fa.getFournisseurId() != null && !fa.getFournisseurId().isBlank()) {
                        agg.supplierIds.add(fa.getFournisseurId());
                    }
                    if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getQuantiteAchetee() > 0
                        && ligneAchat.getPrixAchatUnitaireHT() != null && ligneAchat.getPrixAchatUnitaireHT() > 0) {
                        double qty = ligneAchat.getQuantiteAchetee();
                        double prix = ligneAchat.getPrixAchatUnitaireHT();
                        agg.qtyBuy += qty;
                        agg.sumBuy += prix * qty;
                    }
                }
            }
        } else if (bc.getLignesAchat() != null) {
            for (LigneAchat ligneAchat : bc.getLignesAchat()) {
                if (!matchesProduct(ligneAchat, productRef, designation, unite)) {
                    continue;
                }
                agg.matched = true;
                if (bc.getFournisseurId() != null && !bc.getFournisseurId().isBlank()) {
                    agg.supplierIds.add(bc.getFournisseurId());
                }
                if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getQuantiteAchetee() > 0
                    && ligneAchat.getPrixAchatUnitaireHT() != null && ligneAchat.getPrixAchatUnitaireHT() > 0) {
                    double qty = ligneAchat.getQuantiteAchetee();
                    double prix = ligneAchat.getPrixAchatUnitaireHT();
                    agg.qtyBuy += qty;
                    agg.sumBuy += prix * qty;
                }
            }
        }

        if (bc.getClientsVente() != null) {
            for (ClientVente clientVente : bc.getClientsVente()) {
                if (clientVente == null || clientVente.getLignesVente() == null) {
                    continue;
                }
                for (LigneVente ligneVente : clientVente.getLignesVente()) {
                    if (!matchesProduct(ligneVente, productRef, designation, unite)) {
                        continue;
                    }
                    agg.matched = true;
                    if (clientVente.getClientId() != null && !clientVente.getClientId().isBlank()) {
                        agg.clientIds.add(clientVente.getClientId());
                    }
                    if (ligneVente.getQuantiteVendue() != null && ligneVente.getQuantiteVendue() > 0
                        && ligneVente.getPrixVenteUnitaireHT() != null && ligneVente.getPrixVenteUnitaireHT() > 0) {
                        double qty = ligneVente.getQuantiteVendue();
                        double prix = ligneVente.getPrixVenteUnitaireHT();
                        agg.qtySell += qty;
                        agg.sumSell += prix * qty;
                    }
                }
            }
        }

        if (bc.getLignes() != null) {
            for (LineItem li : bc.getLignes()) {
                if (li == null || !matchesProductLineItem(li, productRef, designation, unite)) {
                    continue;
                }
                agg.matched = true;
                if (bc.getFournisseurId() != null && !bc.getFournisseurId().isBlank()) {
                    agg.supplierIds.add(bc.getFournisseurId());
                }
                if (bc.getClientId() != null && !bc.getClientId().isBlank()) {
                    agg.clientIds.add(bc.getClientId());
                }
                if (li.getQuantiteAchetee() != null && li.getQuantiteAchetee() > 0
                    && li.getPrixAchatUnitaireHT() != null && li.getPrixAchatUnitaireHT() > 0) {
                    double qty = li.getQuantiteAchetee();
                    double prix = li.getPrixAchatUnitaireHT();
                    agg.qtyBuy += qty;
                    agg.sumBuy += prix * qty;
                }
                if (li.getQuantiteVendue() != null && li.getQuantiteVendue() > 0
                    && li.getPrixVenteUnitaireHT() != null && li.getPrixVenteUnitaireHT() > 0) {
                    double qty = li.getQuantiteVendue();
                    double prix = li.getPrixVenteUnitaireHT();
                    agg.qtySell += qty;
                    agg.sumSell += prix * qty;
                }
            }
        }
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    
    /**
     * Vérifie si une ligne d'achat correspond au produit recherché
     */
    private boolean matchesProduct(LigneAchat ligne, String productRef, String designation, String unite) {
        String searchRef = norm(productRef);
        String searchDesignation = norm(designation);
        String searchUnite = normUnite(unite);

        String lineRef = norm(ligne != null ? ligne.getProduitRef() : null);
        String lineDesignation = norm(ligne != null ? ligne.getDesignation() : null);
        String lineUnite = normUnite(ligne != null ? ligne.getUnite() : null);

        // Stratégie:
        // - si ref renseignée des deux côtés -> match par ref (robuste aux espaces/casse)
        // - sinon fallback sur designation + unite
        if (!searchRef.isEmpty() && !lineRef.isEmpty()) {
            return searchRef.equalsIgnoreCase(lineRef);
        }
        if (searchDesignation.isEmpty() || lineDesignation.isEmpty()) return false;
        return searchDesignation.equalsIgnoreCase(lineDesignation) && searchUnite.equalsIgnoreCase(lineUnite);
    }
    
    /**
     * Vérifie si une ligne de vente correspond au produit recherché
     */
    private boolean matchesProduct(LigneVente ligne, String productRef, String designation, String unite) {
        String searchRef = norm(productRef);
        String searchDesignation = norm(designation);
        String searchUnite = normUnite(unite);

        String lineRef = norm(ligne != null ? ligne.getProduitRef() : null);
        String lineDesignation = norm(ligne != null ? ligne.getDesignation() : null);
        String lineUnite = normUnite(ligne != null ? ligne.getUnite() : null);

        if (!searchRef.isEmpty() && !lineRef.isEmpty()) {
            return searchRef.equalsIgnoreCase(lineRef);
        }
        if (searchDesignation.isEmpty() || lineDesignation.isEmpty()) return false;
        return searchDesignation.equalsIgnoreCase(lineDesignation) && searchUnite.equalsIgnoreCase(lineUnite);
    }

    private boolean matchesProductLineItem(LineItem ligne, String productRef, String designation, String unite) {
        String searchRef = norm(productRef);
        String searchDesignation = norm(designation);
        String searchUnite = normUnite(unite);

        String lineRef = norm(ligne != null ? ligne.getProduitRef() : null);
        String lineDesignation = norm(ligne != null ? ligne.getDesignation() : null);
        String lineUnite = normUnite(ligne != null ? ligne.getUnite() : null);

        if (!searchRef.isEmpty() && !lineRef.isEmpty()) {
            return searchRef.equalsIgnoreCase(lineRef);
        }
        if (searchDesignation.isEmpty() || lineDesignation.isEmpty()) return false;
        return searchDesignation.equalsIgnoreCase(lineDesignation) && searchUnite.equalsIgnoreCase(lineUnite);
    }

    private String norm(String s) {
        return s == null ? "" : s.trim();
    }

    private String normUnite(String unite) {
        String u = norm(unite);
        return u.isEmpty() ? "U" : u;
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

