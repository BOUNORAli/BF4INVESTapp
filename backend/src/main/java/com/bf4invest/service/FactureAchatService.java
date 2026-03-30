package com.bf4invest.service;

import com.bf4invest.config.AppConfig;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.LineItem;
import com.bf4invest.model.PrevisionPaiement;
import com.bf4invest.model.Product;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FactureAchatService {
    
    private final FactureAchatRepository factureRepository;
    private final BandeCommandeRepository bandeCommandeRepository;
    private final AppConfig appConfig;
    private final AuditService auditService;
    private final ProductService productService;
    private final CalculComptableService calculComptableService;
    private final SoldeService soldeService;
    private final SupplierService supplierService;
    private final ComptabiliteService comptabiliteService;
    
    public List<FactureAchat> findAll() {
        List<FactureAchat> factures = factureRepository.findAll();
        // Recalculer les champs comptables pour toutes les factures
        factures.forEach(facture -> {
            try {
                // Toujours recalculer pour s'assurer que les champs sont à jour
                calculComptableService.calculerFactureAchat(facture);
                // Sauvegarder pour persister les calculs
                factureRepository.save(facture);
            } catch (Exception e) {
                // Ignorer les erreurs de calcul pour ne pas bloquer la récupération
                log.warn("Erreur lors du calcul comptable pour facture achat {}: {}", facture.getId(), e.getMessage());
            }
        });
        return factures;
    }
    
    public Optional<FactureAchat> findById(String id) {
        return factureRepository.findById(id)
                .map(facture -> {
                    // Recalculer les champs comptables
                    try {
                        calculComptableService.calculerFactureAchat(facture);
                        // Sauvegarder pour persister les calculs
                        return factureRepository.save(facture);
                    } catch (Exception e) {
                        log.warn("Erreur lors du calcul comptable pour facture achat {}: {}", id, e.getMessage());
                        return facture;
                    }
                });
    }
    
    public FactureAchat create(FactureAchat facture) {
        // ========== VALIDATION ET GESTION DES AVOIRS ==========
        if (Boolean.TRUE.equals(facture.getEstAvoir())) {
            // Initialiser les champs avoir si nécessaire
            if (facture.getTypeFacture() == null || facture.getTypeFacture().isEmpty()) {
                facture.setTypeFacture("AVOIR");
            }
            
            // Validation : si c'est un avoir, s'assurer que les montants sont négatifs
            if (facture.getTotalHT() != null && facture.getTotalHT() > 0) {
                log.warn("Avoir achat avec montant HT positif, inversion appliquée");
                facture.setTotalHT(-facture.getTotalHT());
            }
            if (facture.getTotalTTC() != null && facture.getTotalTTC() > 0) {
                log.warn("Avoir achat avec montant TTC positif, inversion appliquée");
                facture.setTotalTTC(-facture.getTotalTTC());
            }
            
            // Valider la liaison avec la facture d'origine si fournie
            if (facture.getFactureOrigineId() != null && !facture.getFactureOrigineId().isEmpty()) {
                Optional<FactureAchat> factureOrigine = factureRepository.findById(facture.getFactureOrigineId());
                if (factureOrigine.isPresent()) {
                    // Empêcher qu'un avoir annule un autre avoir
                    if (Boolean.TRUE.equals(factureOrigine.get().getEstAvoir())) {
                        throw new IllegalArgumentException("Un avoir ne peut pas annuler un autre avoir");
                    }
                    facture.setNumeroFactureOrigine(factureOrigine.get().getNumeroFactureAchat());
                } else {
                    log.warn("Facture d'origine non trouvée pour avoir: " + facture.getFactureOrigineId());
                }
            }
        } else {
            // Si ce n'est pas un avoir, s'assurer que les flags sont corrects
            if (facture.getTypeFacture() == null || facture.getTypeFacture().isEmpty()) {
                facture.setTypeFacture("NORMALE");
            }
            if (facture.getEstAvoir() == null) {
                facture.setEstAvoir(false);
            }
        }
        // ========================================================
        
        // Générer le numéro si non fourni
        if (facture.getNumeroFactureAchat() == null || facture.getNumeroFactureAchat().isEmpty()) {
            if (Boolean.TRUE.equals(facture.getEstAvoir())) {
                // Préfixe spécial pour avoirs
                facture.setNumeroFactureAchat("AVOIR-" + generateFactureNumber(facture.getDateFacture()));
            } else {
                facture.setNumeroFactureAchat(generateFactureNumber(facture.getDateFacture()));
            }
        }
        
        // Calculer la date d'échéance (dateFacture + 2 mois)
        if (facture.getDateFacture() != null) {
            LocalDate echeance = facture.getDateFacture().plusMonths(2);
            facture.setDateEcheance(echeance);
        }
        
        // PRIORITÉ 1: Si la facture est liée à une BC, utiliser les totaux de la BC
        syncTotalsFromBC(facture);
        
        // PRIORITÉ 2: Si les totaux ne sont pas encore définis (pas de BC liée), calculer depuis les lignes ou totaux fournis
        if (facture.getTotalHT() == null && facture.getTotalTTC() == null) {
            calculateTotals(facture);
        } else {
            // Si les totaux sont déjà définis (par syncTotalsFromBC), s'assurer que la TVA est calculée
            if (facture.getTotalHT() != null && facture.getTotalTTC() != null && facture.getTotalTVA() == null) {
                facture.setTotalTVA(NumberUtils.roundTo2Decimals(facture.getTotalTTC() - facture.getTotalHT()));
            }
        }
        
        // Pour les avoirs, s'assurer que les totaux calculés sont négatifs
        if (Boolean.TRUE.equals(facture.getEstAvoir())) {
            if (facture.getTotalHT() != null && facture.getTotalHT() > 0) {
                facture.setTotalHT(-facture.getTotalHT());
            }
            if (facture.getTotalTTC() != null && facture.getTotalTTC() > 0) {
                facture.setTotalTTC(-facture.getTotalTTC());
            }
            if (facture.getTotalTVA() != null && facture.getTotalTVA() > 0) {
                facture.setTotalTVA(-facture.getTotalTVA());
            }
        }
        
        // Calculer les champs comptables selon les formules Excel
        calculComptableService.calculerFactureAchat(facture);
        
        // Initialiser état paiement
        if (facture.getEtatPaiement() == null) {
            facture.setEtatPaiement("non_regle");
        }
        
        facture.setCreatedAt(LocalDateTime.now());
        facture.setUpdatedAt(LocalDateTime.now());
        
        FactureAchat saved = factureRepository.save(facture);
        
        // Mettre à jour le stock si demandé
        if (Boolean.TRUE.equals(saved.getAjouterAuStock()) && saved.getLignes() != null) {
            updateStockFromFacture(saved);
        }
        
        // Journaliser la création
        String libelleAudit = Boolean.TRUE.equals(saved.getEstAvoir()) ? 
            "Avoir Achat " + saved.getNumeroFactureAchat() + " créé" : 
            "Facture Achat " + saved.getNumeroFactureAchat() + " créée";
        auditService.logCreate("FactureAchat", saved.getId(), 
            libelleAudit + " - Montant: " + saved.getTotalTTC() + " MAD");
        
        // Enregistrer la transaction dans le solde
        if (saved.getFournisseurId() != null && saved.getTotalTTC() != null) {
            try {
                supplierService.findById(saved.getFournisseurId()).ifPresent(supplier -> {
                    String typeTransaction = Boolean.TRUE.equals(saved.getEstAvoir()) ? 
                        "AVOIR_ACHAT" : "FACTURE_ACHAT";
                    String libelle = Boolean.TRUE.equals(saved.getEstAvoir()) ? 
                        "Avoir achat " + saved.getNumeroFactureAchat() : 
                        "Facture achat " + saved.getNumeroFactureAchat();
                    
                    soldeService.enregistrerTransaction(
                            typeTransaction,
                            saved.getTotalTTC(), // Déjà négatif pour avoir
                            saved.getFournisseurId(),
                            "FOURNISSEUR",
                            supplier.getNom(),
                            saved.getId(),
                            saved.getNumeroFactureAchat(),
                            libelle,
                            saved.getDateFacture() // Utiliser la date de la facture (DATE FAC VTE) au lieu de la date actuelle
                    );
                });
            } catch (Exception e) {
                log.warn("Erreur lors de l'enregistrement de la transaction solde pour facture achat {}: {}", saved.getId(), e.getMessage());
            }
        }
        
        // Générer l'écriture comptable
        try {
            if (Boolean.TRUE.equals(saved.getEstAvoir())) {
                comptabiliteService.genererEcritureAvoirAchat(saved);
            } else {
                comptabiliteService.genererEcritureFactureAchat(saved);
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la génération de l'écriture comptable pour facture achat {}: {}", saved.getId(), e.getMessage());
        }
        
        return saved;
    }
    
    /**
     * Lie un avoir à une facture d'origine
     */
    public void linkAvoirToFacture(String avoirId, String factureOrigineId) {
        FactureAchat avoir = factureRepository.findById(avoirId)
            .orElseThrow(() -> new RuntimeException("Avoir non trouvé avec id: " + avoirId));
        
        if (!Boolean.TRUE.equals(avoir.getEstAvoir())) {
            throw new IllegalArgumentException("La facture avec id " + avoirId + " n'est pas un avoir");
        }
        
        FactureAchat origine = factureRepository.findById(factureOrigineId)
            .orElseThrow(() -> new RuntimeException("Facture d'origine non trouvée avec id: " + factureOrigineId));
        
        // Empêcher qu'un avoir annule un autre avoir
        if (Boolean.TRUE.equals(origine.getEstAvoir())) {
            throw new IllegalArgumentException("Un avoir ne peut pas annuler un autre avoir");
        }
        
        avoir.setFactureOrigineId(factureOrigineId);
        avoir.setNumeroFactureOrigine(origine.getNumeroFactureAchat());
        avoir.setUpdatedAt(LocalDateTime.now());
        
        factureRepository.save(avoir);
        
        auditService.logUpdate("FactureAchat", avoirId, null,
            "Avoir " + avoir.getNumeroFactureAchat() + " lié à la facture " + origine.getNumeroFactureAchat());
    }
    
    /**
     * Récupère tous les avoirs liés à une facture
     */
    public List<FactureAchat> getAvoirsByFacture(String factureId) {
        return factureRepository.findByFactureOrigineId(factureId);
    }
    
    /**
     * Récupère tous les avoirs
     */
    public List<FactureAchat> getAllAvoirs() {
        return factureRepository.findByEstAvoirTrue();
    }
    
    public FactureAchat update(String id, FactureAchat facture) {
        return factureRepository.findById(id)
                .map(existing -> {
                    if (facture.getDateFacture() != null) {
                        existing.setDateFacture(facture.getDateFacture());
                        existing.setDateEcheance(facture.getDateFacture().plusMonths(2));
                    }
                    if (facture.getBandeCommandeId() != null) {
                        existing.setBandeCommandeId(facture.getBandeCommandeId());
                    }
                    if (facture.getFournisseurId() != null) {
                        existing.setFournisseurId(facture.getFournisseurId());
                    }
                    if (facture.getAjouterAuStock() != null) {
                        existing.setAjouterAuStock(facture.getAjouterAuStock());
                    }
                    // Ne mettre à jour les lignes que si elles sont fournies et non vides
                    if (facture.getLignes() != null && !facture.getLignes().isEmpty()) {
                        existing.setLignes(facture.getLignes());
                        // Recalculer les totaux seulement si les lignes sont fournies
                        calculateTotals(existing);
                    } else {
                        // Si les lignes ne sont pas fournies, préserver les totaux existants
                        // et mettre à jour seulement totalHT et totalTTC si fournis (y compris 0)
                        if (facture.getTotalHT() != null) {
                            existing.setTotalHT(facture.getTotalHT());
                        }
                        if (facture.getTotalTTC() != null) {
                            existing.setTotalTTC(facture.getTotalTTC());
                            // Recalculer la TVA si nécessaire
                            if (facture.getTotalHT() != null) {
                                existing.setTotalTVA(facture.getTotalTTC() - facture.getTotalHT());
                            } else {
                                // Si totalHT n'est pas fourni mais totalTTC oui, calculer à partir du totalHT existant
                                if (existing.getTotalHT() != null) {
                                    existing.setTotalTVA(facture.getTotalTTC() - existing.getTotalHT());
                                }
                            }
                        }
                        // Si aucun total n'est fourni (null dans le payload), les totaux existants sont préservés automatiquement
                    }
                    if (facture.getModePaiement() != null) {
                        existing.setModePaiement(facture.getModePaiement());
                    }
                    
                    // Sauvegarder si l'utilisateur a fourni un état de paiement explicite
                    boolean etatPaiementExplicite = facture.getEtatPaiement() != null;
                    String etatPaiementUtilisateur = null;
                    if (etatPaiementExplicite) {
                        etatPaiementUtilisateur = facture.getEtatPaiement();
                        existing.setEtatPaiement(etatPaiementUtilisateur);
                    }
                    
                    // Ne pas mettre à jour l'état de paiement si l'utilisateur l'a fourni explicitement
                    calculateMontantRestant(existing, !etatPaiementExplicite);
                    
                    // Recalculer les champs comptables selon les formules Excel
                    calculComptableService.calculerFactureAchat(existing);
                    
                    // Log pour déboguer
                    log.info("🔵 FactureAchatService.update - Champs calculés après calcul: tvaMois={}, solde={}, totalTTCApresRG={}, bilan={}", 
                        existing.getTvaMois(), existing.getSolde(), existing.getTotalTTCApresRG(), existing.getBilan());
                    
                    // Restaurer l'état de paiement de l'utilisateur si fourni
                    if (etatPaiementExplicite) {
                        existing.setEtatPaiement(etatPaiementUtilisateur);
                    }
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    FactureAchat saved = factureRepository.save(existing);
                    
                    // Log après sauvegarde
                    log.info("🔵 FactureAchatService.update - Champs calculés après sauvegarde: tvaMois={}, solde={}, totalTTCApresRG={}, bilan={}", 
                        saved.getTvaMois(), saved.getSolde(), saved.getTotalTTCApresRG(), saved.getBilan());
                    
                    // Mettre à jour le stock si demandé (seulement si ajouterAuStock est passé à true)
                    if (Boolean.TRUE.equals(facture.getAjouterAuStock()) && saved.getLignes() != null) {
                        updateStockFromFacture(saved);
                    }
                    
                    // Journaliser la modification
                    auditService.logUpdate("FactureAchat", saved.getId(), null, 
                        "Facture Achat " + saved.getNumeroFactureAchat() + " modifiée - Statut: " + saved.getEtatPaiement());
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("Facture achat not found with id: " + id));
    }
    
    public void delete(String id) {
        // Journaliser avant suppression
        factureRepository.findById(id).ifPresent(f -> {
            auditService.logDelete("FactureAchat", id, "Facture Achat " + f.getNumeroFactureAchat() + " supprimée");
        });
        factureRepository.deleteById(id);
    }
    
    public List<FactureAchat> findOverdue() {
        return factureRepository.findByDateEcheanceLessThanEqual(LocalDate.now());
    }
    
    private String generateFactureNumber(LocalDate date) {
        String year = String.valueOf(date.getYear());
        long count = factureRepository.findAll().stream()
                .filter(f -> f.getDateFacture() != null && f.getDateFacture().getYear() == date.getYear())
                .count();
        String sequence = String.format("%03d", count + 1);
        return String.format("FA-%s-%s", year, sequence);
    }
    
    private void calculateTotals(FactureAchat facture) {
        // Si les totaux sont déjà fournis explicitement, les préserver (sauf si des lignes sont aussi fournies)
        boolean totalsProvided = (facture.getTotalHT() != null && facture.getTotalHT() > 0) || 
                                 (facture.getTotalTTC() != null && facture.getTotalTTC() > 0);
        boolean hasLines = facture.getLignes() != null && !facture.getLignes().isEmpty();
        
        // Si des lignes sont fournies, calculer à partir des lignes (priorité)
        if (hasLines) {
            double totalHT = 0.0;
            double totalTVA = 0.0;
            
            for (LineItem ligne : facture.getLignes()) {
                double qte = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
                double prixHT = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
                double tvaRate = ligne.getTva() != null ? ligne.getTva() / 100.0 : 0.0;
                
                double ht = NumberUtils.roundTo2Decimals(qte * prixHT);
                double tva = NumberUtils.roundTo2Decimals(ht * tvaRate);
                double ttc = NumberUtils.roundTo2Decimals(ht + tva);
                
                ligne.setTotalHT(ht);
                ligne.setTotalTTC(ttc);
                
                totalHT += ht;
                totalTVA += tva;
            }
            
            facture.setTotalHT(NumberUtils.roundTo2Decimals(totalHT));
            facture.setTotalTVA(NumberUtils.roundTo2Decimals(totalTVA));
            facture.setTotalTTC(NumberUtils.roundTo2Decimals(totalHT + totalTVA));
        } else if (totalsProvided) {
            // Pas de lignes mais totaux fournis explicitement, les préserver et calculer TVA si nécessaire
            if (facture.getTotalHT() == null || facture.getTotalHT() == 0.0) {
                // Seul TTC fourni, estimer HT (TVA 20% par défaut)
                double estimatedHT = NumberUtils.roundTo2Decimals(facture.getTotalTTC() / 1.2);
                facture.setTotalHT(estimatedHT);
                facture.setTotalTVA(NumberUtils.roundTo2Decimals(facture.getTotalTTC() - estimatedHT));
            } else if (facture.getTotalTTC() == null || facture.getTotalTTC() == 0.0) {
                // Seul HT fourni, calculer TTC (TVA 20% par défaut)
                facture.setTotalTVA(NumberUtils.roundTo2Decimals(facture.getTotalHT() * 0.2));
                facture.setTotalTTC(NumberUtils.roundTo2Decimals(facture.getTotalHT() + facture.getTotalTVA()));
            } else {
                // Les deux fournis, calculer TVA
                facture.setTotalTVA(NumberUtils.roundTo2Decimals(facture.getTotalTTC() - facture.getTotalHT()));
            }
            // Arrondir les totaux fournis
            if (facture.getTotalHT() != null) {
                facture.setTotalHT(NumberUtils.roundTo2Decimals(facture.getTotalHT()));
            }
            if (facture.getTotalTTC() != null) {
                facture.setTotalTTC(NumberUtils.roundTo2Decimals(facture.getTotalTTC()));
            }
        } else {
            // Aucune ligne ni total fourni, mettre à 0
            facture.setTotalHT(0.0);
            facture.setTotalTVA(0.0);
            facture.setTotalTTC(0.0);
        }
        
        calculateMontantRestant(facture);
    }
    
    /**
     * Synchronise les totaux de la facture avec les totaux de la BC liée
     * PRIORITÉ 1: Si la facture est liée à une BC, utiliser les totaux de la BC
     */
    private void syncTotalsFromBC(FactureAchat facture) {
        if (facture == null) {
            return;
        }
        
        BandeCommande bc = null;
        
        // Chercher la BC par bandeCommandeId
        if (facture.getBandeCommandeId() != null && !facture.getBandeCommandeId().isEmpty()) {
            bc = bandeCommandeRepository.findById(facture.getBandeCommandeId()).orElse(null);
        }
        
        // Fallback: chercher par bcReference
        if (bc == null && facture.getBcReference() != null && !facture.getBcReference().isEmpty()) {
            bc = bandeCommandeRepository.findByNumeroBC(facture.getBcReference()).orElse(null);
        }
        
        if (bc != null && bc.getTotalAchatHT() != null && bc.getTotalAchatTTC() != null) {
            // Utiliser les totaux de la BC
            facture.setTotalHT(NumberUtils.roundTo2Decimals(bc.getTotalAchatHT()));
            facture.setTotalTTC(NumberUtils.roundTo2Decimals(bc.getTotalAchatTTC()));
            facture.setTotalTVA(NumberUtils.roundTo2Decimals(bc.getTotalAchatTTC() - bc.getTotalAchatHT()));
            
            log.info("🔵 FactureAchatService.syncTotalsFromBC - Facture {} synchronisée avec BC {}: HT={}, TTC={}, TVA={}",
                facture.getNumeroFactureAchat() != null ? facture.getNumeroFactureAchat() : "(nouvelle)",
                bc.getNumeroBC(), facture.getTotalHT(), facture.getTotalTTC(), facture.getTotalTVA());
        } else if (bc != null) {
            log.debug("FactureAchatService.syncTotalsFromBC - BC {} trouvée mais totaux non disponibles", 
                bc.getNumeroBC() != null ? bc.getNumeroBC() : bc.getId());
        }
    }
    
    private void calculateMontantRestant(FactureAchat facture) {
        calculateMontantRestant(facture, true);
    }
    
    private void calculateMontantRestant(FactureAchat facture, boolean updateEtatPaiement) {
        double totalPaiements = 0.0;
        if (facture.getPaiements() != null) {
            totalPaiements = facture.getPaiements().stream()
                    .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                    .sum();
        }
        
        double montantRestant = NumberUtils.roundTo2Decimals(facture.getTotalTTC() - totalPaiements);
        facture.setMontantRestant(montantRestant);
        
        // Mettre à jour l'état de paiement seulement si demandé
        if (updateEtatPaiement) {
            if (montantRestant <= 0) {
                facture.setEtatPaiement("regle");
            } else if (totalPaiements > 0) {
                facture.setEtatPaiement("partiellement_regle");
            } else {
                facture.setEtatPaiement("non_regle");
            }
        }
    }
    
    /**
     * Met à jour le stock des produits à partir des lignes de la facture d'achat
     */
    private void updateStockFromFacture(FactureAchat facture) {
        if (facture.getLignes() == null || facture.getLignes().isEmpty()) {
            return;
        }
        
        for (LineItem ligne : facture.getLignes()) {
            if (ligne.getProduitRef() == null || ligne.getProduitRef().isEmpty()) {
                log.warn("⚠️ FactureAchatService.updateStockFromFacture - Ligne sans produitRef, ignorée");
                continue;
            }
            
            Double quantite = ligne.getQuantiteAchetee();
            if (quantite == null || quantite <= 0) {
                log.warn("⚠️ FactureAchatService.updateStockFromFacture - Quantité invalide pour produitRef: {}, ignorée", ligne.getProduitRef());
                continue;
            }
            
            try {
                // Stock est en Double : on conserve la précision décimale
                Product updated = productService.updateStockByRef(ligne.getProduitRef(), quantite);
                if (updated != null) {
                    log.info("✅ FactureAchatService.updateStockFromFacture - Stock mis à jour pour produitRef: {}, quantité ajoutée: {}, nouveau stock: {}", 
                        ligne.getProduitRef(), quantite, updated.getQuantiteEnStock());
                } else {
                    log.warn("⚠️ FactureAchatService.updateStockFromFacture - Produit non trouvé avec refArticle: {}", ligne.getProduitRef());
                }
            } catch (Exception e) {
                log.error("❌ FactureAchatService.updateStockFromFacture - Erreur lors de la mise à jour du stock pour produitRef: {}", 
                    ligne.getProduitRef(), e);
                // Ne pas bloquer la création de la facture en cas d'erreur
            }
        }
    }
    
    public PrevisionPaiement addPrevision(String factureId, PrevisionPaiement prevision) {
        FactureAchat facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture achat not found with id: " + factureId));
        
        if (facture.getPrevisionsPaiement() == null) {
            facture.setPrevisionsPaiement(new java.util.ArrayList<>());
        }
        
        // Générer un ID si non fourni
        if (prevision.getId() == null || prevision.getId().isEmpty()) {
            prevision.setId(java.util.UUID.randomUUID().toString());
        }
        
        // Initialiser createdAt si non fourni
        if (prevision.getCreatedAt() == null) {
            prevision.setCreatedAt(LocalDateTime.now());
        }
        
        // Initialiser statut si non fourni
        if (prevision.getStatut() == null) {
            prevision.setStatut("EN_ATTENTE");
        }
        
        // Initialiser les champs de suivi de paiement
        if (prevision.getMontantPaye() == null) {
            prevision.setMontantPaye(0.0);
        }
        if (prevision.getMontantRestant() == null && prevision.getMontantPrevu() != null) {
            prevision.setMontantRestant(prevision.getMontantPrevu());
        }
        
        facture.getPrevisionsPaiement().add(prevision);
        facture.setUpdatedAt(LocalDateTime.now());
        factureRepository.save(facture);
        
        // Log d'audit
        String details = String.format("Prévision de paiement ajoutée: %.2f MAD prévu le %s%s", 
            prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0,
            prevision.getDatePrevue() != null ? prevision.getDatePrevue().toString() : "N/A",
            prevision.getDateRappel() != null ? " (rappel: " + prevision.getDateRappel().toString() + ")" : "");
        auditService.logCreate("PrevisionPaiement", prevision.getId(), 
            "Prévision pour facture achat " + facture.getNumeroFactureAchat() + " - " + details);
        
        return prevision;
    }
    
    public PrevisionPaiement updatePrevision(String factureId, String previsionId, PrevisionPaiement previsionUpdate) {
        FactureAchat facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture achat not found with id: " + factureId));
        
        if (facture.getPrevisionsPaiement() == null) {
            throw new RuntimeException("Aucune prévision trouvée pour cette facture");
        }
        
        PrevisionPaiement prevision = facture.getPrevisionsPaiement().stream()
            .filter(p -> previsionId.equals(p.getId()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Prévision not found with id: " + previsionId));
        
        // Sauvegarder l'ancienne valeur pour l'audit
        String oldValue = String.format("%.2f MAD le %s%s", 
            prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0,
            prevision.getDatePrevue() != null ? prevision.getDatePrevue().toString() : "N/A",
            prevision.getDateRappel() != null ? " (rappel: " + prevision.getDateRappel().toString() + ")" : "");
        
        // Mettre à jour les champs
        if (previsionUpdate.getDatePrevue() != null) {
            prevision.setDatePrevue(previsionUpdate.getDatePrevue());
        }
        if (previsionUpdate.getMontantPrevu() != null) {
            prevision.setMontantPrevu(previsionUpdate.getMontantPrevu());
        }
        if (previsionUpdate.getStatut() != null) {
            prevision.setStatut(previsionUpdate.getStatut());
        }
        if (previsionUpdate.getNotes() != null) {
            prevision.setNotes(previsionUpdate.getNotes());
        }
        // dateRappel peut être null pour supprimer le rappel
        prevision.setDateRappel(previsionUpdate.getDateRappel());
        
        facture.setUpdatedAt(LocalDateTime.now());
        factureRepository.save(facture);
        
        // Log d'audit
        String newValue = String.format("%.2f MAD le %s%s", 
            prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0,
            prevision.getDatePrevue() != null ? prevision.getDatePrevue().toString() : "N/A",
            prevision.getDateRappel() != null ? " (rappel: " + prevision.getDateRappel().toString() + ")" : "");
        auditService.logUpdate("PrevisionPaiement", previsionId, oldValue, 
            "Prévision pour facture achat " + facture.getNumeroFactureAchat() + " - " + newValue);
        
        return prevision;
    }
    
    public void deletePrevision(String factureId, String previsionId) {
        FactureAchat facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture achat not found with id: " + factureId));
        
        if (facture.getPrevisionsPaiement() == null) {
            throw new RuntimeException("Aucune prévision trouvée pour cette facture");
        }
        
        PrevisionPaiement previsionToDelete = facture.getPrevisionsPaiement().stream()
            .filter(p -> previsionId.equals(p.getId()))
            .findFirst()
            .orElse(null);
        
        boolean removed = facture.getPrevisionsPaiement().removeIf(p -> previsionId.equals(p.getId()));
        
        if (!removed) {
            throw new RuntimeException("Prévision not found with id: " + previsionId);
        }
        
        facture.setUpdatedAt(LocalDateTime.now());
        factureRepository.save(facture);
        
        // Log d'audit
        if (previsionToDelete != null) {
            String details = String.format("Prévision de paiement supprimée: %.2f MAD prévu le %s", 
                previsionToDelete.getMontantPrevu() != null ? previsionToDelete.getMontantPrevu() : 0.0,
                previsionToDelete.getDatePrevue() != null ? previsionToDelete.getDatePrevue().toString() : "N/A");
            auditService.logDelete("PrevisionPaiement", previsionId, 
                "Prévision pour facture achat " + facture.getNumeroFactureAchat() + " - " + details);
        }
    }
}




