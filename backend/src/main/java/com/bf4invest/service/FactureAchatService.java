package com.bf4invest.service;

import com.bf4invest.config.AppConfig;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.LineItem;
import com.bf4invest.repository.FactureAchatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FactureAchatService {
    
    private final FactureAchatRepository factureRepository;
    private final AppConfig appConfig;
    
    public List<FactureAchat> findAll() {
        return factureRepository.findAll();
    }
    
    public Optional<FactureAchat> findById(String id) {
        return factureRepository.findById(id);
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
        
        // Initialiser état paiement
        if (facture.getEtatPaiement() == null) {
            facture.setEtatPaiement("non_regle");
        }
        
        facture.setCreatedAt(LocalDateTime.now());
        facture.setUpdatedAt(LocalDateTime.now());
        
        return factureRepository.save(facture);
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
                    
                    // Restaurer l'état de paiement de l'utilisateur si fourni
                    if (etatPaiementExplicite) {
                        existing.setEtatPaiement(etatPaiementUtilisateur);
                    }
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    return factureRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Facture achat not found with id: " + id));
    }
    
    public void delete(String id) {
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
        if (facture.getLignes() == null || facture.getLignes().isEmpty()) {
            facture.setTotalHT(0.0);
            facture.setTotalTVA(0.0);
            facture.setTotalTTC(0.0);
            return;
        }
        
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
}




