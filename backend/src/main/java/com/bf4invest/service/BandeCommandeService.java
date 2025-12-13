package com.bf4invest.service;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.LineItem;
import com.bf4invest.repository.BandeCommandeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BandeCommandeService {
    
    private final BandeCommandeRepository bcRepository;
    private final AuditService auditService;
    
    public List<BandeCommande> findAll() {
        return bcRepository.findAll();
    }
    
    public Optional<BandeCommande> findById(String id) {
        return bcRepository.findById(id);
    }
    
    public BandeCommande create(BandeCommande bc) {
        // Générer le numéro BC si non fourni
        if (bc.getNumeroBC() == null || bc.getNumeroBC().isEmpty()) {
            bc.setNumeroBC(generateBCNumber(bc.getDateBC()));
        }
        
        // Calculer les totaux
        calculateTotals(bc);
        
        bc.setCreatedAt(LocalDateTime.now());
        bc.setUpdatedAt(LocalDateTime.now());
        
        BandeCommande saved = bcRepository.save(bc);
        
        // Journaliser la création
        auditService.logCreate("BandeCommande", saved.getId(), 
            "BC " + saved.getNumeroBC() + " créée - Total: " + saved.getTotalVenteTTC() + " MAD");
        
        return saved;
    }
    
    public BandeCommande update(String id, BandeCommande bc) {
        return bcRepository.findById(id)
                .map(existing -> {
                    String oldEtat = existing.getEtat();
                    
                    existing.setDateBC(bc.getDateBC());
                    existing.setClientId(bc.getClientId());
                    existing.setFournisseurId(bc.getFournisseurId());
                    existing.setLignes(bc.getLignes());
                    existing.setEtat(bc.getEtat());
                    existing.setNotes(bc.getNotes());
                    if (bc.getModePaiement() != null) {
                        existing.setModePaiement(bc.getModePaiement());
                    }
                    
                    // Recalculer les totaux
                    calculateTotals(existing);
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    BandeCommande saved = bcRepository.save(existing);
                    
                    // Journaliser la modification
                    String details = "BC " + saved.getNumeroBC() + " modifiée";
                    if (!oldEtat.equals(bc.getEtat())) {
                        details += " - Statut: " + oldEtat + " -> " + bc.getEtat();
                    }
                    auditService.logUpdate("BandeCommande", saved.getId(), oldEtat, details);
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("BC not found with id: " + id));
    }
    
    public void delete(String id) {
        // Récupérer la BC avant suppression pour le log
        bcRepository.findById(id).ifPresent(bc -> {
            auditService.logDelete("BandeCommande", id, "BC " + bc.getNumeroBC() + " supprimée");
        });
        bcRepository.deleteById(id);
    }
    
    private String generateBCNumber(LocalDate date) {
        String year = String.valueOf(date.getYear());
        // Simple sequence based on count for this year
        long count = bcRepository.findAll().stream()
                .filter(bc -> bc.getDateBC() != null && bc.getDateBC().getYear() == date.getYear())
                .count();
        String sequence = String.format("%04d", count + 1);
        return String.format("BF4-BC-%s-%s", year, sequence);
    }
    
    private void calculateTotals(BandeCommande bc) {
        if (bc.getLignes() == null || bc.getLignes().isEmpty()) {
            bc.setTotalAchatHT(0.0);
            bc.setTotalAchatTTC(0.0);
            bc.setTotalVenteHT(0.0);
            bc.setTotalVenteTTC(0.0);
            bc.setMargeTotale(0.0);
            bc.setMargePourcentage(0.0);
            return;
        }
        
        double totalAchatHT = 0.0;
        double totalAchatTTC = 0.0;
        double totalVenteHT = 0.0;
        double totalVenteTTC = 0.0;
        
        for (LineItem ligne : bc.getLignes()) {
            // Calculer les totaux par ligne
            double qteAchat = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
            double qteVente = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue() : 0;
            double prixAchat = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
            double prixVente = ligne.getPrixVenteUnitaireHT() != null ? ligne.getPrixVenteUnitaireHT() : 0;
            double tvaRate = ligne.getTva() != null ? ligne.getTva() / 100.0 : 0.0;
            
            double htAchat = qteAchat * prixAchat;
            double ttcAchat = htAchat * (1 + tvaRate);
            
            double htVente = qteVente * prixVente;
            double ttcVente = htVente * (1 + tvaRate);
            
            ligne.setTotalHT(htVente);
            ligne.setTotalTTC(ttcVente);
            
            // Marge unitaire
            if (prixAchat > 0) {
                double margeUnitaire = prixVente - prixAchat;
                double margePourcent = (margeUnitaire / prixAchat) * 100;
                ligne.setMargeUnitaire(margeUnitaire);
                ligne.setMargePourcentage(margePourcent);
            }
            
            totalAchatHT += htAchat;
            totalAchatTTC += ttcAchat;
            totalVenteHT += htVente;
            totalVenteTTC += ttcVente;
        }
        
        bc.setTotalAchatHT(totalAchatHT);
        bc.setTotalAchatTTC(totalAchatTTC);
        bc.setTotalVenteHT(totalVenteHT);
        bc.setTotalVenteTTC(totalVenteTTC);
        
        // Marge totale
        bc.setMargeTotale(totalVenteHT - totalAchatHT);
        
        // Marge en pourcentage
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(((totalVenteHT - totalAchatHT) / totalAchatHT) * 100);
        } else {
            bc.setMargePourcentage(0.0);
        }
    }
}




