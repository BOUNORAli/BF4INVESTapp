package com.bf4invest.service;

import com.bf4invest.config.AppConfig;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.LineItem;
import com.bf4invest.model.PrevisionPaiement;
import com.bf4invest.model.Product;
import com.bf4invest.repository.FactureAchatRepository;
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
    private final AppConfig appConfig;
    private final AuditService auditService;
    private final ProductService productService;
    private final CalculComptableService calculComptableService;
    private final SoldeService soldeService;
    private final SupplierService supplierService;
    
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
        // Générer le numéro si non fourni
        if (facture.getNumeroFactureAchat() == null || facture.getNumeroFactureAchat().isEmpty()) {
            facture.setNumeroFactureAchat(generateFactureNumber(facture.getDateFacture()));
        }
        
        // Calculer la date d'échéance (dateFacture + 2 mois)
        if (facture.getDateFacture() != null) {
            LocalDate echeance = facture.getDateFacture().plusMonths(2);
            facture.setDateEcheance(echeance);
        }
        
        // Calculer les totaux
        calculateTotals(facture);
        
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
        auditService.logCreate("FactureAchat", saved.getId(), 
            "Facture Achat " + saved.getNumeroFactureAchat() + " créée - Montant: " + saved.getTotalTTC() + " MAD");
        
        // Enregistrer la transaction dans le solde
        if (saved.getFournisseurId() != null && saved.getTotalTTC() != null) {
            try {
                supplierService.findById(saved.getFournisseurId()).ifPresent(supplier -> {
                    soldeService.enregistrerTransaction(
                            "FACTURE_ACHAT",
                            saved.getTotalTTC(),
                            saved.getFournisseurId(),
                            "FOURNISSEUR",
                            supplier.getNom(),
                            saved.getId(),
                            saved.getNumeroFactureAchat(),
                            "Facture achat " + saved.getNumeroFactureAchat()
                    );
                });
            } catch (Exception e) {
                log.warn("Erreur lors de l'enregistrement de la transaction solde pour facture achat {}: {}", saved.getId(), e.getMessage());
            }
        }
        
        return saved;
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
                
                double ht = qte * prixHT;
                double tva = ht * tvaRate;
                
                ligne.setTotalHT(ht);
                ligne.setTotalTTC(ht + tva);
                
                totalHT += ht;
                totalTVA += tva;
            }
            
            facture.setTotalHT(totalHT);
            facture.setTotalTVA(totalTVA);
            facture.setTotalTTC(totalHT + totalTVA);
        } else if (totalsProvided) {
            // Pas de lignes mais totaux fournis explicitement, les préserver et calculer TVA si nécessaire
            if (facture.getTotalHT() == null || facture.getTotalHT() == 0.0) {
                // Seul TTC fourni, estimer HT (TVA 20% par défaut)
                double estimatedHT = facture.getTotalTTC() / 1.2;
                facture.setTotalHT(estimatedHT);
                facture.setTotalTVA(facture.getTotalTTC() - estimatedHT);
            } else if (facture.getTotalTTC() == null || facture.getTotalTTC() == 0.0) {
                // Seul HT fourni, calculer TTC (TVA 20% par défaut)
                facture.setTotalTVA(facture.getTotalHT() * 0.2);
                facture.setTotalTTC(facture.getTotalHT() + facture.getTotalTVA());
            } else {
                // Les deux fournis, calculer TVA
                facture.setTotalTVA(facture.getTotalTTC() - facture.getTotalHT());
            }
        } else {
            // Aucune ligne ni total fourni, mettre à 0
            facture.setTotalHT(0.0);
            facture.setTotalTVA(0.0);
            facture.setTotalTTC(0.0);
        }
        
        calculateMontantRestant(facture);
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
        
        double montantRestant = facture.getTotalTTC() - totalPaiements;
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
            
            Integer quantite = ligne.getQuantiteAchetee();
            if (quantite == null || quantite <= 0) {
                log.warn("⚠️ FactureAchatService.updateStockFromFacture - Quantité invalide pour produitRef: {}, ignorée", ligne.getProduitRef());
                continue;
            }
            
            try {
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




