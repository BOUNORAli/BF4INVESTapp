package com.bf4invest.service;

import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaiementService {
    
    private final PaiementRepository paiementRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final CalculComptableService calculComptableService;
    private final SoldeService soldeService;
    private final ClientService clientService;
    private final SupplierService supplierService;
    private final AuditService auditService;
    
    public Paiement create(Paiement paiement) {
        // Calculer les champs comptables selon les formules Excel
        calculComptableService.calculerPaiement(paiement);
        
        paiement.setCreatedAt(LocalDateTime.now());
        Paiement saved = paiementRepository.save(paiement);
        
        // Mettre à jour la facture associée
        updateFacturePaymentStatus(saved);
        
        // Log d'audit après avoir mis à jour la facture
        final String[] factureNumero = {""};
        final String[] partenaireNom = {""};
        if (saved.getFactureVenteId() != null) {
            factureVenteRepository.findById(saved.getFactureVenteId()).ifPresent(facture -> {
                factureNumero[0] = facture.getNumeroFactureVente();
                if (facture.getClientId() != null) {
                    clientService.findById(facture.getClientId()).ifPresent(client -> {
                        partenaireNom[0] = client.getNom();
                    });
                }
            });
        } else if (saved.getFactureAchatId() != null) {
            factureAchatRepository.findById(saved.getFactureAchatId()).ifPresent(facture -> {
                factureNumero[0] = facture.getNumeroFactureAchat();
                if (facture.getFournisseurId() != null) {
                    supplierService.findById(facture.getFournisseurId()).ifPresent(supplier -> {
                        partenaireNom[0] = supplier.getNom();
                    });
                }
            });
        }
        String details = String.format("Paiement de %.2f MAD - %s - Facture: %s", 
            saved.getMontant() != null ? saved.getMontant() : 0.0,
            partenaireNom[0].isEmpty() ? "N/A" : partenaireNom[0],
            factureNumero[0].isEmpty() ? "N/A" : factureNumero[0]);
        auditService.logCreate("Paiement", saved.getId(), details);
        
        // Enregistrer la transaction dans le solde et mettre à jour les soldes dans le paiement
        if (saved.getMontant() != null && saved.getMontant() > 0) {
            try {
                String typeTransaction;
                String referenceNumero = saved.getReference() != null ? saved.getReference() : "Paiement";
                
                if (saved.getFactureVenteId() != null) {
                    // Paiement client
                    typeTransaction = "PAIEMENT_CLIENT";
                    factureVenteRepository.findById(saved.getFactureVenteId()).ifPresent(facture -> {
                        if (facture.getClientId() != null) {
                            clientService.findById(facture.getClientId()).ifPresent(client -> {
                                var historique = soldeService.enregistrerTransaction(
                                        typeTransaction,
                                        saved.getMontant(),
                                        facture.getClientId(),
                                        "CLIENT",
                                        client.getNom(),
                                        saved.getId(),
                                        referenceNumero,
                                        "Paiement client - " + facture.getNumeroFactureVente()
                                );
                                
                                // Mettre à jour les soldes dans le paiement
                                saved.setSoldeGlobalApres(historique.getSoldeGlobalApres());
                                saved.setSoldePartenaireApres(historique.getSoldePartenaireApres());
                                paiementRepository.save(saved);
                            });
                        }
                    });
                } else if (saved.getFactureAchatId() != null) {
                    // Paiement fournisseur
                    typeTransaction = "PAIEMENT_FOURNISSEUR";
                    factureAchatRepository.findById(saved.getFactureAchatId()).ifPresent(facture -> {
                        if (facture.getFournisseurId() != null) {
                            supplierService.findById(facture.getFournisseurId()).ifPresent(supplier -> {
                                var historique = soldeService.enregistrerTransaction(
                                        typeTransaction,
                                        saved.getMontant(),
                                        facture.getFournisseurId(),
                                        "FOURNISSEUR",
                                        supplier.getNom(),
                                        saved.getId(),
                                        referenceNumero,
                                        "Paiement fournisseur - " + facture.getNumeroFactureAchat()
                                );
                                
                                // Mettre à jour les soldes dans le paiement
                                saved.setSoldeGlobalApres(historique.getSoldeGlobalApres());
                                saved.setSoldePartenaireApres(historique.getSoldePartenaireApres());
                                paiementRepository.save(saved);
                            });
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Erreur lors de l'enregistrement de la transaction solde pour paiement {}: {}", saved.getId(), e.getMessage());
            }
        }
        
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




