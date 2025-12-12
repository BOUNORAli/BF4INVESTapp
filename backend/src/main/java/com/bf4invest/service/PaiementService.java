package com.bf4invest.service;

import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaiementService {
    
    private final PaiementRepository paiementRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    
    public Paiement create(Paiement paiement) {
        paiement.setCreatedAt(LocalDateTime.now());
        Paiement saved = paiementRepository.save(paiement);
        
        // Mettre à jour la facture associée
        updateFacturePaymentStatus(saved);
        
        return saved;
    }
    
    public List<Paiement> findByFactureAchatId(String factureAchatId) {
        return paiementRepository.findByFactureAchatId(factureAchatId);
    }
    
    public List<Paiement> findByFactureVenteId(String factureVenteId) {
        return paiementRepository.findByFactureVenteId(factureVenteId);
    }
    
    private void updateFacturePaymentStatus(Paiement paiement) {
        if (paiement.getFactureAchatId() != null) {
            factureAchatRepository.findById(paiement.getFactureAchatId())
                    .ifPresent(facture -> {
                        // Récupérer tous les paiements pour cette facture
                        List<Paiement> paiements = paiementRepository.findByFactureAchatId(facture.getId());
                        double totalPaiements = paiements.stream()
                                .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                                .sum();
                        
                        double montantRestant = facture.getTotalTTC() - totalPaiements;
                        
                        if (montantRestant <= 0) {
                            facture.setEtatPaiement("regle");
                        } else if (totalPaiements > 0) {
                            facture.setEtatPaiement("partiellement_regle");
                        }
                        
                        facture.setMontantRestant(montantRestant);
                        if (facture.getPaiements() == null) {
                            facture.setPaiements(List.of());
                        }
                        facture.getPaiements().add(paiement);
                        
                        factureAchatRepository.save(facture);
                    });
        }
        
        if (paiement.getFactureVenteId() != null) {
            factureVenteRepository.findById(paiement.getFactureVenteId())
                    .ifPresent(facture -> {
                        List<Paiement> paiements = paiementRepository.findByFactureVenteId(facture.getId());
                        double totalPaiements = paiements.stream()
                                .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                                .sum();
                        
                        double montantRestant = facture.getTotalTTC() - totalPaiements;
                        
                        if (montantRestant <= 0) {
                            facture.setEtatPaiement("regle");
                        } else if (totalPaiements > 0) {
                            facture.setEtatPaiement("partiellement_regle");
                        }
                        
                        facture.setMontantRestant(montantRestant);
                        if (facture.getPaiements() == null) {
                            facture.setPaiements(List.of());
                        }
                        facture.getPaiements().add(paiement);
                        
                        factureVenteRepository.save(facture);
                    });
        }
    }
}




