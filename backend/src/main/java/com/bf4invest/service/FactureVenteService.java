package com.bf4invest.service;

import com.bf4invest.config.AppConfig;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.LineItem;
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
    
    public List<FactureVente> findAll() {
        return factureRepository.findAll();
    }
    
    public Optional<FactureVente> findById(String id) {
        return factureRepository.findById(id);
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
        
        // Initialiser état paiement
        if (facture.getEtatPaiement() == null) {
            facture.setEtatPaiement("non_regle");
        }
        
        facture.setCreatedAt(LocalDateTime.now());
        facture.setUpdatedAt(LocalDateTime.now());
        
        FactureVente saved = factureRepository.save(facture);
        
        // Journaliser la création
        auditService.logCreate("FactureVente", saved.getId(), 
            "Facture Vente " + saved.getNumeroFactureVente() + " créée - Montant: " + saved.getTotalTTC() + " MAD");
        
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
        String year = String.valueOf(date.getYear());
        long count = factureRepository.findAll().stream()
                .filter(f -> f.getDateFacture() != null && f.getDateFacture().getYear() == date.getYear())
                .count();
        String sequence = String.format("%03d", count + 1);
        return String.format("FV-%s-%s", year, sequence);
    }
    
    private void calculateTotals(FactureVente facture) {
        if (facture.getLignes() == null || facture.getLignes().isEmpty()) {
            facture.setTotalHT(0.0);
            facture.setTotalTVA(0.0);
            facture.setTotalTTC(0.0);
            return;
        }
        
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
}




