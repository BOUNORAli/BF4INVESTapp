package com.bf4invest.service;

import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ComptabiliteService comptabiliteService;
    
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
                
                if (saved.getFactureVenteId() != null) {
                    // Paiement client
                    typeTransaction = "PAIEMENT_CLIENT";
                    factureVenteRepository.findById(saved.getFactureVenteId()).ifPresent(facture -> {
                        if (facture.getClientId() != null) {
                            clientService.findById(facture.getClientId()).ifPresent(client -> {
                                // Utiliser le numéro de facture comme référence (pas la référence de l'opération comptable)
                                String referenceNumero = facture.getNumeroFactureVente() != null 
                                        ? facture.getNumeroFactureVente() 
                                        : (saved.getReference() != null ? saved.getReference() : "Paiement");
                                
                                var historique = soldeService.enregistrerTransaction(
                                        typeTransaction,
                                        saved.getMontant(),
                                        facture.getClientId(),
                                        "CLIENT",
                                        client.getNom(),
                                        saved.getId(),
                                        referenceNumero,
                                        "Paiement client - " + facture.getNumeroFactureVente(),
                                        saved.getDate() // Utiliser la date du paiement (colonne DATE de l'Excel)
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
                                // Utiliser le numéro de facture comme référence (pas la référence de l'opération comptable)
                                String referenceNumero = facture.getNumeroFactureAchat() != null 
                                        ? facture.getNumeroFactureAchat() 
                                        : (saved.getReference() != null ? saved.getReference() : "Paiement");
                                
                                var historique = soldeService.enregistrerTransaction(
                                        typeTransaction,
                                        saved.getMontant(),
                                        facture.getFournisseurId(),
                                        "FOURNISSEUR",
                                        supplier.getNom(),
                                        saved.getId(),
                                        referenceNumero,
                                        "Paiement fournisseur - " + facture.getNumeroFactureAchat(),
                                        saved.getDate() // Utiliser la date du paiement (colonne DATE de l'Excel)
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
        
        // Générer l'écriture comptable
        try {
            comptabiliteService.genererEcriturePaiement(saved);
        } catch (Exception e) {
            log.warn("Erreur lors de la génération de l'écriture comptable pour paiement {}: {}", saved.getId(), e.getMessage());
        }
        
        return saved;
    }
    
    public Paiement update(String id, Paiement patch) {
        Paiement existing = paiementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paiement not found with id: " + id));
        
        // On autorise surtout la mise à jour des métadonnées (date, mode, référence, notes)
        if (patch.getDate() != null) {
            existing.setDate(patch.getDate());
        }
        if (patch.getMode() != null && !patch.getMode().isBlank()) {
            existing.setMode(patch.getMode());
        }
        if (patch.getReference() != null) {
            existing.setReference(patch.getReference());
        }
        if (patch.getNotes() != null) {
            existing.setNotes(patch.getNotes());
        }
        
        // On évite de modifier le lien facture et le montant pour ne pas casser la comptabilité
        
        // Recalcul des champs comptables de base
        calculComptableService.calculerPaiement(existing);
        
        Paiement saved = paiementRepository.save(existing);
        
        // Recalcul de la facture liée
        updateFacturePaymentStatus(saved);
        
        // Audit simple
        auditService.logUpdate("Paiement", saved.getId(), null, "Paiement mis à jour (date/mode/référence/notes)");
        
        return saved;
    }
    
    public void delete(String id) {
        Paiement paiement = paiementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paiement not found with id: " + id));
        
        String factureAchatId = paiement.getFactureAchatId();
        String factureVenteId = paiement.getFactureVenteId();
        
        paiementRepository.deleteById(id);
        
        // Recalcul de la facture achat si nécessaire
        if (factureAchatId != null) {
            factureAchatRepository.findById(factureAchatId).ifPresent(facture -> {
                recomputeFactureAchatFromPayments(facture);
                factureAchatRepository.save(facture);
            });
        }
        
        // Recalcul de la facture vente si nécessaire
        if (factureVenteId != null) {
            factureVenteRepository.findById(factureVenteId).ifPresent(facture -> {
                recomputeFactureVenteFromPayments(facture);
                factureVenteRepository.save(facture);
            });
        }
        
        auditService.logDelete("Paiement", id, "Paiement supprimé");
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
                        recomputeFactureAchatFromPayments(facture);
                        factureAchatRepository.save(facture);
                    });
        }
        
        if (paiement.getFactureVenteId() != null) {
            factureVenteRepository.findById(paiement.getFactureVenteId())
                    .ifPresent(facture -> {
                        recomputeFactureVenteFromPayments(facture);
                        factureVenteRepository.save(facture);
                    });
        }
    }
    
    private void recomputeFactureAchatFromPayments(FactureAchat facture) {
        List<Paiement> paiements = paiementRepository.findByFactureAchatId(facture.getId());
        double totalPaiements = NumberUtils.roundTo2Decimals(paiements.stream()
                .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                .sum());
        
        double totalTtc = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
        double montantRestant = NumberUtils.roundTo2Decimals(totalTtc - totalPaiements);
        
        if (montantRestant <= 0 && totalTtc > 0) {
            facture.setEtatPaiement("regle");
        } else if (totalPaiements > 0) {
            facture.setEtatPaiement("partiellement_regle");
        } else {
            facture.setEtatPaiement("non_regle");
        }
        
        facture.setMontantRestant(montantRestant);
        facture.setPaiements(new ArrayList<>(paiements));
        
        // Appliquer la déduction automatique des prévisions à partir du total payé
        double montantPaiementTotal = totalPaiements;
        deduirePrevisions(facture.getPrevisionsPaiement(), montantPaiementTotal);
    }
    
    private void recomputeFactureVenteFromPayments(FactureVente facture) {
        List<Paiement> paiements = paiementRepository.findByFactureVenteId(facture.getId());
        double totalPaiements = NumberUtils.roundTo2Decimals(paiements.stream()
                .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                .sum());
        
        double totalTtc = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
        double montantRestant = NumberUtils.roundTo2Decimals(totalTtc - totalPaiements);
        
        if (montantRestant <= 0 && totalTtc > 0) {
            facture.setEtatPaiement("regle");
        } else if (totalPaiements > 0) {
            facture.setEtatPaiement("partiellement_regle");
        } else {
            facture.setEtatPaiement("non_regle");
        }
        
        facture.setMontantRestant(montantRestant);
        facture.setPaiements(new ArrayList<>(paiements));
        
        double montantPaiementTotal = totalPaiements;
        deduirePrevisions(facture.getPrevisionsPaiement(), montantPaiementTotal);
    }
    
    /**
     * Déduit automatiquement les prévisions selon la logique FIFO par date d'échéance.
     * Les prévisions sont triées par date d'échéance (la plus proche en premier).
     * Le montant du paiement est déduit des prévisions dans l'ordre jusqu'à épuisement.
     */
    private void deduirePrevisions(List<com.bf4invest.model.PrevisionPaiement> previsions, Double montantPaiement) {
        if (previsions == null || previsions.isEmpty() || montantPaiement == null || montantPaiement <= 0) {
            return;
        }
        
        // Initialiser les champs si nécessaire
        for (com.bf4invest.model.PrevisionPaiement prevision : previsions) {
            if (prevision.getMontantPaye() == null) {
                prevision.setMontantPaye(0.0);
            }
            if (prevision.getMontantRestant() == null && prevision.getMontantPrevu() != null) {
                prevision.setMontantRestant(prevision.getMontantPrevu());
            }
            if (prevision.getStatut() == null || prevision.getStatut().equals("PREVU") || prevision.getStatut().equals("EN_RETARD")) {
                // Ne traiter que les prévisions non encore payées
                if (prevision.getMontantRestant() == null || prevision.getMontantRestant() > 0) {
                    prevision.setStatut("EN_ATTENTE");
                }
            }
        }
        
        // Trier les prévisions par date d'échéance (la plus proche en premier)
        List<com.bf4invest.model.PrevisionPaiement> previsionsTriees = previsions.stream()
                .filter(p -> p.getDatePrevue() != null)
                .sorted((p1, p2) -> p1.getDatePrevue().compareTo(p2.getDatePrevue()))
                .collect(java.util.stream.Collectors.toList());
        
        // Ajouter les prévisions sans date à la fin
        List<com.bf4invest.model.PrevisionPaiement> previsionsSansDate = previsions.stream()
                .filter(p -> p.getDatePrevue() == null)
                .collect(java.util.stream.Collectors.toList());
        previsionsTriees.addAll(previsionsSansDate);
        
        double montantRestant = montantPaiement;
        
        // Parcourir les prévisions dans l'ordre et déduire le montant
        for (com.bf4invest.model.PrevisionPaiement prevision : previsionsTriees) {
            if (montantRestant <= 0) {
                break;
            }
            
            // Initialiser montantRestant si null
            if (prevision.getMontantRestant() == null) {
                if (prevision.getMontantPrevu() != null) {
                    prevision.setMontantRestant(prevision.getMontantPrevu());
                } else {
                    continue;
                }
            }
            
            double montantRestantPrevision = prevision.getMontantRestant();
            
            if (montantRestantPrevision <= 0) {
                // Prévision déjà payée, passer à la suivante
                continue;
            }
            
            if (montantRestant >= montantRestantPrevision) {
                // Le paiement couvre entièrement cette prévision
                prevision.setMontantPaye(NumberUtils.roundTo2Decimals(prevision.getMontantPaye() + montantRestantPrevision));
                prevision.setMontantRestant(0.0);
                prevision.setStatut("PAYEE");
                montantRestant = NumberUtils.roundTo2Decimals(montantRestant - montantRestantPrevision);
            } else {
                // Le paiement couvre partiellement cette prévision
                prevision.setMontantPaye(NumberUtils.roundTo2Decimals(prevision.getMontantPaye() + montantRestant));
                prevision.setMontantRestant(NumberUtils.roundTo2Decimals(montantRestantPrevision - montantRestant));
                prevision.setStatut("PARTIELLE");
                montantRestant = 0.0;
            }
        }
    }
}




