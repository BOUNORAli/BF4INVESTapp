package com.bf4invest.service;

import com.bf4invest.config.AppConfig;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.LineItem;
import com.bf4invest.model.PrevisionPaiement;
import com.bf4invest.model.Product;
import com.bf4invest.repository.FactureVenteRepository;
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
public class FactureVenteService {
    
    private final FactureVenteRepository factureRepository;
    private final AppConfig appConfig;
    private final AuditService auditService;
    private final ProductService productService;
    private final CalculComptableService calculComptableService;
    private final SoldeService soldeService;
    private final ClientService clientService;
    private final ComptabiliteService comptabiliteService;
    
    public List<FactureVente> findAll() {
        List<FactureVente> factures = factureRepository.findAll();
        // Recalculer les champs comptables pour toutes les factures
        factures.forEach(facture -> {
            try {
                // Toujours recalculer pour s'assurer que les champs sont à jour
                calculComptableService.calculerFactureVente(facture);
                // Sauvegarder pour persister les calculs
                factureRepository.save(facture);
            } catch (Exception e) {
                // Ignorer les erreurs de calcul pour ne pas bloquer la récupération
                log.warn("Erreur lors du calcul comptable pour facture vente {}: {}", facture.getId(), e.getMessage());
            }
        });
        return factures;
    }
    
    public Optional<FactureVente> findById(String id) {
        return factureRepository.findById(id)
                .map(facture -> {
                    // Recalculer les champs comptables
                    try {
                        calculComptableService.calculerFactureVente(facture);
                        // Sauvegarder pour persister les calculs
                        return factureRepository.save(facture);
                    } catch (Exception e) {
                        log.warn("Erreur lors du calcul comptable pour facture vente {}: {}", id, e.getMessage());
                        return facture;
                    }
                });
    }
    
    public FactureVente create(FactureVente facture) {
        // Générer le numéro si non fourni
        if (facture.getNumeroFactureVente() == null || facture.getNumeroFactureVente().isEmpty()) {
            facture.setNumeroFactureVente(generateFactureNumber(facture.getDateFacture()));
        }
        
        // Calculer la date d'échéance (dateFacture + délai paramétrable, défaut 30j)
        if (facture.getDateFacture() != null) {
            int paymentTermDays = appConfig.getDefaultPaymentTermDays();
            LocalDate echeance = facture.getDateFacture().plusDays(paymentTermDays);
            facture.setDateEcheance(echeance);
        }
        
        // Calculer les totaux
        calculateTotals(facture);
        
        // Calculer les champs comptables selon les formules Excel
        calculComptableService.calculerFactureVente(facture);
        
        // Initialiser état paiement
        if (facture.getEtatPaiement() == null) {
            facture.setEtatPaiement("non_regle");
        }
        
        facture.setCreatedAt(LocalDateTime.now());
        facture.setUpdatedAt(LocalDateTime.now());
        
        FactureVente saved = factureRepository.save(facture);
        
        // Décrémenter le stock des produits vendus
        if (saved.getLignes() != null && !saved.getLignes().isEmpty()) {
            updateStockFromFacture(saved);
        }
        
        // Journaliser la création
        auditService.logCreate("FactureVente", saved.getId(), 
            "Facture Vente " + saved.getNumeroFactureVente() + " créée - Montant: " + saved.getTotalTTC() + " MAD");
        
        // Enregistrer la transaction dans le solde
        if (saved.getClientId() != null && saved.getTotalTTC() != null) {
            try {
                clientService.findById(saved.getClientId()).ifPresent(client -> {
                    soldeService.enregistrerTransaction(
                            "FACTURE_VENTE",
                            saved.getTotalTTC(),
                            saved.getClientId(),
                            "CLIENT",
                            client.getNom(),
                            saved.getId(),
                            saved.getNumeroFactureVente(),
                            "Facture vente " + saved.getNumeroFactureVente()
                    );
                });
            } catch (Exception e) {
                log.warn("Erreur lors de l'enregistrement de la transaction solde pour facture vente {}: {}", saved.getId(), e.getMessage());
            }
        }
        
        // Générer l'écriture comptable
        try {
            comptabiliteService.genererEcritureFactureVente(saved);
        } catch (Exception e) {
            log.warn("Erreur lors de la génération de l'écriture comptable pour facture vente {}: {}", saved.getId(), e.getMessage());
        }
        
        return saved;
    }
    
    public FactureVente update(String id, FactureVente facture) {
        log.info("🔵 FactureVenteService.update - ID: {}", id);
        log.info("🔵 FactureVenteService.update - Facture reçue: numeroFactureVente={}, dateFacture={}, clientId={}", 
            facture.getNumeroFactureVente(), facture.getDateFacture(), facture.getClientId());
        log.info("🔵 FactureVenteService.update - Montants reçus: totalHT={}, totalTTC={}", 
            facture.getTotalHT(), facture.getTotalTTC());
        log.info("🔵 FactureVenteService.update - Lignes reçues: {}", 
            facture.getLignes() != null ? facture.getLignes().size() + " lignes" : "null");
        
        return factureRepository.findById(id)
                .map(existing -> {
                    log.info("🔵 FactureVenteService.update - Facture existante trouvée");
                    log.info("🔵 FactureVenteService.update - Montants existants AVANT update: totalHT={}, totalTTC={}", 
                        existing.getTotalHT(), existing.getTotalTTC());
                    
                    if (facture.getDateFacture() != null) {
                        existing.setDateFacture(facture.getDateFacture());
                        existing.setDateEcheance(facture.getDateFacture().plusDays(appConfig.getDefaultPaymentTermDays()));
                    }
                    if (facture.getBandeCommandeId() != null) {
                        existing.setBandeCommandeId(facture.getBandeCommandeId());
                    }
                    if (facture.getClientId() != null) {
                        existing.setClientId(facture.getClientId());
                    }
                    // Ne mettre à jour les lignes que si elles sont fournies et non vides
                    if (facture.getLignes() != null && !facture.getLignes().isEmpty()) {
                        log.info("🔵 FactureVenteService.update - Lignes fournies, recalcul des totaux");
                        existing.setLignes(facture.getLignes());
                        // Recalculer les totaux seulement si les lignes sont fournies
                        calculateTotals(existing);
                        log.info("🔵 FactureVenteService.update - Totaux après recalcul: totalHT={}, totalTTC={}", 
                            existing.getTotalHT(), existing.getTotalTTC());
                    } else {
                        log.info("🔵 FactureVenteService.update - Aucune ligne fournie, mise à jour des totaux si fournis");
                        // Si les lignes ne sont pas fournies, mettre à jour totalHT et totalTTC si fournis
                        // Si totalHT et totalTTC sont fournis dans la requête, les utiliser (même s'ils sont 0)
                        // Si non fournis (null), préserver les valeurs existantes
                        boolean totalHTProvided = facture.getTotalHT() != null;
                        boolean totalTTCProvided = facture.getTotalTTC() != null;
                        
                        log.info("🔵 FactureVenteService.update - totalHT fourni: {}, valeur: {}", 
                            totalHTProvided, facture.getTotalHT());
                        log.info("🔵 FactureVenteService.update - totalTTC fourni: {}, valeur: {}", 
                            totalTTCProvided, facture.getTotalTTC());
                        
                        if (totalHTProvided) {
                            log.info("🔵 FactureVenteService.update - Mise à jour totalHT: {} -> {}", 
                                existing.getTotalHT(), facture.getTotalHT());
                            existing.setTotalHT(facture.getTotalHT());
                        }
                        if (totalTTCProvided) {
                            log.info("🔵 FactureVenteService.update - Mise à jour totalTTC: {} -> {}", 
                                existing.getTotalTTC(), facture.getTotalTTC());
                            existing.setTotalTTC(facture.getTotalTTC());
                            // Recalculer la TVA si nécessaire
                            if (totalHTProvided) {
                                double tva = facture.getTotalTTC() - facture.getTotalHT();
                                log.info("🔵 FactureVenteService.update - Calcul TVA: {} - {} = {}", 
                                    facture.getTotalTTC(), facture.getTotalHT(), tva);
                                existing.setTotalTVA(tva);
                            } else if (existing.getTotalHT() != null) {
                                // Si totalHT n'est pas fourni mais totalTTC oui, calculer à partir du totalHT existant
                                double tva = facture.getTotalTTC() - existing.getTotalHT();
                                log.info("🔵 FactureVenteService.update - Calcul TVA avec totalHT existant: {} - {} = {}", 
                                    facture.getTotalTTC(), existing.getTotalHT(), tva);
                                existing.setTotalTVA(tva);
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
                        log.info("🔵 FactureVenteService.update - Mise à jour état paiement: {} -> {}", 
                            existing.getEtatPaiement(), etatPaiementUtilisateur);
                        existing.setEtatPaiement(etatPaiementUtilisateur);
                    }
                    
                    log.info("🔵 FactureVenteService.update - Montants AVANT calculateMontantRestant: totalHT={}, totalTTC={}", 
                        existing.getTotalHT(), existing.getTotalTTC());
                    // Ne pas mettre à jour l'état de paiement si l'utilisateur l'a fourni explicitement
                    calculateMontantRestant(existing, !etatPaiementExplicite);
                    
                    // Recalculer les champs comptables selon les formules Excel
                    calculComptableService.calculerFactureVente(existing);
                    
                    log.info("🔵 FactureVenteService.update - Montants APRÈS calculateMontantRestant: totalHT={}, totalTTC={}", 
                        existing.getTotalHT(), existing.getTotalTTC());
                    
                    // Restaurer l'état de paiement de l'utilisateur si fourni
                    if (etatPaiementExplicite) {
                        existing.setEtatPaiement(etatPaiementUtilisateur);
                        log.info("🔵 FactureVenteService.update - État de paiement restauré: {}", etatPaiementUtilisateur);
                    }
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    
                    log.info("🔵 FactureVenteService.update - Sauvegarde de la facture");
                    FactureVente saved = factureRepository.save(existing);
                    log.info("🔵 FactureVenteService.update - Facture sauvegardée: totalHT={}, totalTTC={}", 
                        saved.getTotalHT(), saved.getTotalTTC());
                    
                    // Décrémenter le stock si les lignes ont été modifiées
                    if (facture.getLignes() != null && !facture.getLignes().isEmpty()) {
                        updateStockFromFacture(saved);
                    }
                    
                    // Journaliser la modification
                    auditService.logUpdate("FactureVente", saved.getId(), null, 
                        "Facture Vente " + saved.getNumeroFactureVente() + " modifiée - Statut: " + saved.getEtatPaiement());
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("Facture vente not found with id: " + id));
    }
    
    public void delete(String id) {
        // Journaliser avant suppression
        factureRepository.findById(id).ifPresent(f -> {
            auditService.logDelete("FactureVente", id, "Facture Vente " + f.getNumeroFactureVente() + " supprimée");
        });
        factureRepository.deleteById(id);
    }
    
    private String generateFactureNumber(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("La date de facture est requise pour générer le numéro");
        }
        
        // 1. Extraire le mois (format MM)
        int month = date.getMonthValue();
        String mois = String.format("%02d", month);
        
        // 2. Extraire l'année (format YYYY)
        int year = date.getYear();
        String annee4chiffres = String.valueOf(year);
        
        // 3. Compter les factures existantes pour ce mois + année
        long count = factureRepository.findAll().stream()
                .filter(f -> {
                    if (f.getDateFacture() == null) return false;
                    return f.getDateFacture().getMonthValue() == month && 
                           f.getDateFacture().getYear() == year;
                })
                .count();
        
        // 4. Générer le numéro séquentiel (toujours 2 chiffres : 01, 02, 03, etc.)
        String numero = String.format("%02d", count + 1);
        
        // 5. Assembler : mois + numéro + "/" + annee4chiffres
        return mois + numero + "/" + annee4chiffres;
    }
    
    private void calculateTotals(FactureVente facture) {
        // Si les totaux sont déjà fournis explicitement, les préserver (sauf si des lignes sont aussi fournies)
        boolean totalsProvided = (facture.getTotalHT() != null && facture.getTotalHT() > 0) || 
                                 (facture.getTotalTTC() != null && facture.getTotalTTC() > 0);
        boolean hasLines = facture.getLignes() != null && !facture.getLignes().isEmpty();
        
        // Si des lignes sont fournies, calculer à partir des lignes (priorité)
        if (hasLines) {
            double totalHT = 0.0;
            double totalTVA = 0.0;
            
            for (LineItem ligne : facture.getLignes()) {
                double qte = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue() : 0;
                double prixHT = ligne.getPrixVenteUnitaireHT() != null ? ligne.getPrixVenteUnitaireHT() : 0;
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
    
    private void calculateMontantRestant(FactureVente facture) {
        calculateMontantRestant(facture, true);
    }
    
    private void calculateMontantRestant(FactureVente facture, boolean updateEtatPaiement) {
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
     * Met à jour le stock des produits à partir des lignes de la facture de vente
     * Décrémente le stock et avertit si le stock est insuffisant (mais permet la vente)
     */
    private void updateStockFromFacture(FactureVente facture) {
        if (facture.getLignes() == null || facture.getLignes().isEmpty()) {
            return;
        }
        
        for (LineItem ligne : facture.getLignes()) {
            if (ligne.getProduitRef() == null || ligne.getProduitRef().isEmpty()) {
                log.warn("⚠️ FactureVenteService.updateStockFromFacture - Ligne sans produitRef, ignorée");
                continue;
            }
            
            Integer quantite = ligne.getQuantiteVendue();
            if (quantite == null || quantite <= 0) {
                log.warn("⚠️ FactureVenteService.updateStockFromFacture - Quantité invalide pour produitRef: {}, ignorée", ligne.getProduitRef());
                continue;
            }
            
            try {
                // Vérifier le stock disponible avant de décrémenter
                Integer stockActuel = productService.getStockByRef(ligne.getProduitRef());
                
                if (stockActuel < quantite) {
                    log.warn("⚠️ FactureVenteService.updateStockFromFacture - Stock insuffisant pour produitRef: {}. Stock actuel: {}, Quantité demandée: {}. La vente est autorisée mais le stock deviendra négatif.", 
                        ligne.getProduitRef(), stockActuel, quantite);
                }
                
                // Décrémenter le stock (même si insuffisant, on permet la vente)
                Product updated = productService.updateStockByRef(ligne.getProduitRef(), -quantite);
                if (updated != null) {
                    Integer nouveauStock = updated.getQuantiteEnStock() != null ? updated.getQuantiteEnStock() : 0;
                    if (nouveauStock < 0) {
                        log.warn("⚠️ FactureVenteService.updateStockFromFacture - Stock négatif pour produitRef: {}, nouveau stock: {}", 
                            ligne.getProduitRef(), nouveauStock);
                    } else {
                        log.info("✅ FactureVenteService.updateStockFromFacture - Stock mis à jour pour produitRef: {}, quantité vendue: {}, nouveau stock: {}", 
                            ligne.getProduitRef(), quantite, nouveauStock);
                    }
                } else {
                    log.warn("⚠️ FactureVenteService.updateStockFromFacture - Produit non trouvé avec refArticle: {}", ligne.getProduitRef());
                }
            } catch (Exception e) {
                log.error("❌ FactureVenteService.updateStockFromFacture - Erreur lors de la mise à jour du stock pour produitRef: {}", 
                    ligne.getProduitRef(), e);
                // Ne pas bloquer la création de la facture en cas d'erreur
            }
        }
    }
    
    public PrevisionPaiement addPrevision(String factureId, PrevisionPaiement prevision) {
        FactureVente facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture vente not found with id: " + factureId));
        
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
            "Prévision pour facture vente " + facture.getNumeroFactureVente() + " - " + details);
        
        return prevision;
    }
    
    public PrevisionPaiement updatePrevision(String factureId, String previsionId, PrevisionPaiement previsionUpdate) {
        FactureVente facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture vente not found with id: " + factureId));
        
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
            "Prévision pour facture vente " + facture.getNumeroFactureVente() + " - " + newValue);
        
        return prevision;
    }
    
    public void deletePrevision(String factureId, String previsionId) {
        FactureVente facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture vente not found with id: " + factureId));
        
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
                "Prévision pour facture vente " + facture.getNumeroFactureVente() + " - " + details);
        }
    }
}




