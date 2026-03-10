package com.bf4invest.service;

import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionMappingService {
    
    private final TransactionBancaireRepository transactionRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final PaiementRepository paiementRepository;
    private final PaiementService paiementService;
    private final EcritureComptableRepository ecritureComptableRepository;
    
    /**
     * Mappe automatiquement les transactions bancaires aux factures/paiements
     */
    @Transactional
    public Map<String, Integer> mapperTransactions(Integer mois, Integer annee) {
        List<TransactionBancaire> transactions = transactionRepository.findByMappedFalseAndMoisAndAnnee(mois, annee);
        
        int mappedCount = 0;
        int paiementsCrees = 0;
        int errors = 0;
        
        for (TransactionBancaire transaction : transactions) {
            try {
                boolean mapped = mapperTransactionToFacture(transaction);
                if (mapped) {
                    mappedCount++;
                    if (transaction.getPaiementId() != null) {
                        paiementsCrees++;
                    }
                }
            } catch (Exception e) {
                log.error("Erreur lors du mapping de la transaction {}: {}", transaction.getId(), e.getMessage(), e);
                errors++;
            }
        }
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", transactions.size());
        stats.put("mapped", mappedCount);
        stats.put("paiementsCrees", paiementsCrees);
        stats.put("errors", errors);
        stats.put("nonMapped", transactions.size() - mappedCount);
        
        log.info("Mapping terminé: {} transactions mappées sur {}, {} paiements créés", 
                mappedCount, transactions.size(), paiementsCrees);
        
        return stats;
    }
    
    /**
     * Tente de mapper une transaction à une facture
     */
    private boolean mapperTransactionToFacture(TransactionBancaire transaction) {
        // Déterminer le montant de la transaction
        Double montantTransaction = getMontantTransaction(transaction);
        
        if (montantTransaction == 0.0) {
            return false;
        }
        
        // Si c'est un crédit, c'est probablement un paiement client (vente)
        if (transaction.getCredit() != null && transaction.getCredit() > 0) {
            return mapperTransactionToFactureVente(transaction, montantTransaction);
        }
        
        // Si c'est un débit, c'est probablement un paiement fournisseur (achat)
        if (transaction.getDebit() != null && transaction.getDebit() > 0) {
            return mapperTransactionToFactureAchat(transaction, montantTransaction);
        }
        
        return false;
    }
    
    /**
     * Mappe une transaction à une facture vente
     */
    private boolean mapperTransactionToFactureVente(TransactionBancaire transaction, Double montant) {
        // Rechercher par montant correspondant (±1% de tolérance)
        double tolerance = montant * 0.01;
        LocalDate dateTransaction = transaction.getDateOperation();
        LocalDate dateDebut = dateTransaction.minusDays(7);
        LocalDate dateFin = dateTransaction.plusDays(7);
        
        List<FactureVente> factures = factureVenteRepository.findAll();
        
        for (FactureVente facture : factures) {
            // Vérifier le montant
            if (facture.getTotalTTC() == null) continue;
            
            double difference = Math.abs(facture.getTotalTTC() - montant);
            if (difference > tolerance) continue;
            
            // Vérifier si la facture n'est pas déjà payée
            List<Paiement> paiementsExistants = paiementRepository.findByFactureVenteId(facture.getId());
            double totalPaiements = paiementsExistants.stream()
                    .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                    .sum();
            
            double montantRestant = facture.getTotalTTC() - totalPaiements;
            if (Math.abs(montantRestant - montant) > tolerance) continue;
            
            // Vérifier la référence si disponible
            if (transaction.getReference() != null && !transaction.getReference().isEmpty()) {
                // Chercher dans les paiements existants si la référence correspond
                boolean referenceMatch = paiementsExistants.stream()
                        .anyMatch(p -> transaction.getReference().equals(p.getReference()));
                if (referenceMatch) continue; // Cette référence est déjà utilisée
            }
            
            // Vérifier le libellé pour des indices (numéro de facture, nom client)
            if (transaction.getLibelle() != null) {
                String libelle = transaction.getLibelle().toLowerCase();
                boolean libelleMatch = libelle.contains(facture.getNumeroFactureVente().toLowerCase());
                
                if (!libelleMatch && facture.getClientId() != null) {
                    // Optionnel: chercher le nom du client dans le libellé
                    // (nécessiterait d'injecter ClientService, à faire si nécessaire)
                }
            }
            
            // Créer le paiement et lier la transaction
            Paiement paiement = creerPaiementDepuisTransaction(transaction, facture, null);
            if (paiement != null) {
                transaction.setFactureVenteId(facture.getId());
                transaction.setPaiementId(paiement.getId());
                transaction.setMapped(true);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Mappe une transaction à une facture achat
     */
    private boolean mapperTransactionToFactureAchat(TransactionBancaire transaction, Double montant) {
        // Rechercher par montant correspondant (±1% de tolérance)
        double tolerance = montant * 0.01;
        LocalDate dateTransaction = transaction.getDateOperation();
        LocalDate dateDebut = dateTransaction.minusDays(7);
        LocalDate dateFin = dateTransaction.plusDays(7);
        
        List<FactureAchat> factures = factureAchatRepository.findAll();
        
        for (FactureAchat facture : factures) {
            // Vérifier le montant
            if (facture.getTotalTTC() == null) continue;
            
            double difference = Math.abs(facture.getTotalTTC() - montant);
            if (difference > tolerance) continue;
            
            // Vérifier si la facture n'est pas déjà payée
            List<Paiement> paiementsExistants = paiementRepository.findByFactureAchatId(facture.getId());
            double totalPaiements = paiementsExistants.stream()
                    .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                    .sum();
            
            double montantRestant = facture.getTotalTTC() - totalPaiements;
            if (Math.abs(montantRestant - montant) > tolerance) continue;
            
            // Vérifier la référence si disponible
            if (transaction.getReference() != null && !transaction.getReference().isEmpty()) {
                boolean referenceMatch = paiementsExistants.stream()
                        .anyMatch(p -> transaction.getReference().equals(p.getReference()));
                if (referenceMatch) continue;
            }
            
            // Vérifier le libellé
            if (transaction.getLibelle() != null) {
                String libelle = transaction.getLibelle().toLowerCase();
                boolean libelleMatch = libelle.contains(facture.getNumeroFactureAchat().toLowerCase());
                // Optionnel: chercher le nom du fournisseur
            }
            
            // Créer le paiement et lier la transaction
            Paiement paiement = creerPaiementDepuisTransaction(transaction, null, facture);
            if (paiement != null) {
                transaction.setFactureAchatId(facture.getId());
                transaction.setPaiementId(paiement.getId());
                transaction.setMapped(true);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Crée un paiement depuis une transaction bancaire et une facture
     */
    private Paiement creerPaiementDepuisTransaction(TransactionBancaire transaction, 
                                                     FactureVente factureVente, 
                                                     FactureAchat factureAchat) {
        try {
            Double montant = transaction.getCredit() != null && transaction.getCredit() > 0 
                    ? transaction.getCredit() 
                    : transaction.getDebit();
            
            if (montant == null || montant <= 0) {
                return null;
            }
            
            Paiement.PaiementBuilder builder = Paiement.builder()
                    .date(transaction.getDateOperation())
                    .montant(montant)
                    .reference(transaction.getReference())
                    .transactionBancaireId(transaction.getId())
                    .mode(determinerModePaiement(transaction.getLibelle()));
            
            if (factureVente != null) {
                builder.factureVenteId(factureVente.getId())
                       .typeMouvement("C")
                       .nature("paiement");
                
                // Récupérer le taux TVA de la facture
                if (factureVente.getTvaRate() != null) {
                    builder.tvaRate(factureVente.getTvaRate());
                }
            } else if (factureAchat != null) {
                builder.factureAchatId(factureAchat.getId())
                       .typeMouvement("F")
                       .nature("paiement");
                
                if (factureAchat.getTvaRate() != null) {
                    builder.tvaRate(factureAchat.getTvaRate());
                }
            } else {
                return null;
            }
            
            Paiement paiement = builder.build();
            
            // Utiliser le service de paiement pour créer le paiement (qui gère les calculs et mises à jour)
            return paiementService.create(paiement);
            
        } catch (Exception e) {
            log.error("Erreur lors de la création du paiement depuis la transaction {}: {}", 
                    transaction.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Détermine le mode de paiement depuis le libellé
     */
    private String determinerModePaiement(String libelle) {
        if (libelle == null) return "virement";
        
        String libelleLower = libelle.toLowerCase();
        
        if (libelleLower.contains("cheque") || libelleLower.contains("chèque")) {
            return "cheque";
        } else if (libelleLower.contains("virement") || libelleLower.contains("vir")) {
            return "virement";
        } else if (libelleLower.contains("lcn")) {
            return "LCN";
        } else if (libelleLower.contains("compensation")) {
            return "compensation";
        } else if (libelleLower.contains("especes") || libelleLower.contains("espèces")) {
            return "especes";
        }
        
        return "virement"; // Par défaut
    }
    
    /**
     * Lie manuellement une transaction à une facture
     */
    @Transactional
    public boolean lierTransactionManuellement(String transactionId, String factureVenteId, String factureAchatId) {
        Optional<TransactionBancaire> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            return false;
        }
        
        TransactionBancaire transaction = transactionOpt.get();
        
        if (factureVenteId != null && !factureVenteId.isEmpty()) {
            Optional<FactureVente> factureOpt = factureVenteRepository.findById(factureVenteId);
            if (factureOpt.isEmpty()) {
                return false;
            }
            
            FactureVente facture = factureOpt.get();
            Double montant = transaction.getCredit() != null && transaction.getCredit() > 0 
                    ? transaction.getCredit() 
                    : transaction.getDebit();
            
            Paiement paiement = creerPaiementDepuisTransaction(transaction, facture, null);
            if (paiement != null) {
                transaction.setFactureVenteId(factureVenteId);
                transaction.setPaiementId(paiement.getId());
                transaction.setMapped(true);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                return true;
            }
        } else if (factureAchatId != null && !factureAchatId.isEmpty()) {
            Optional<FactureAchat> factureOpt = factureAchatRepository.findById(factureAchatId);
            if (factureOpt.isEmpty()) {
                return false;
            }
            
            FactureAchat facture = factureOpt.get();
            Paiement paiement = creerPaiementDepuisTransaction(transaction, null, facture);
            if (paiement != null) {
                transaction.setFactureAchatId(factureAchatId);
                transaction.setPaiementId(paiement.getId());
                transaction.setMapped(true);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Etat de rapprochement: transactions bancaires non mappees, ecritures banque non pointees et ecart.
     */
    public Map<String, Object> getEtatRapprochement(Integer mois, Integer annee) {
        List<TransactionBancaire> transactions = (mois != null && annee != null)
                ? transactionRepository.findByMoisAndAnnee(mois, annee)
                : transactionRepository.findAll();

        List<EcritureComptable> ecrituresBanqueNonPointees = ecritureComptableRepository
                .findByPointageFalseAndLignesCompteCode("5141");

        double soldeBancaire = transactions.stream()
                .mapToDouble(t -> nz(t.getCredit()) - nz(t.getDebit()))
                .sum();
        double soldeComptable = ecrituresBanqueNonPointees.stream()
                .mapToDouble(this::mouvementBanqueEcriture)
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("transactions", transactions);
        result.put("ecrituresBanqueNonPointees", ecrituresBanqueNonPointees);
        result.put("soldeBancaire", soldeBancaire);
        result.put("soldeComptableNonPointe", soldeComptable);
        result.put("ecart", soldeBancaire - soldeComptable);
        return result;
    }

    @Transactional
    public boolean pointerTransactionAvecEcriture(String transactionId, String ecritureId) {
        Optional<TransactionBancaire> transactionOpt = transactionRepository.findById(transactionId);
        Optional<EcritureComptable> ecritureOpt = ecritureComptableRepository.findById(ecritureId);
        if (transactionOpt.isEmpty() || ecritureOpt.isEmpty()) {
            return false;
        }

        TransactionBancaire transaction = transactionOpt.get();
        EcritureComptable ecriture = ecritureOpt.get();
        double montantTransaction = getMontantTransaction(transaction);
        double montantEcriture = Math.abs(mouvementBanqueEcriture(ecriture));
        if (Math.abs(montantTransaction - montantEcriture) > 0.01) {
            return false;
        }

        ecriture.setPointage(true);
        ecriture.setTransactionBancaireId(transaction.getId());
        ecriture.setUpdatedAt(LocalDateTime.now());
        ecritureComptableRepository.save(ecriture);

        transaction.setMapped(true);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        return true;
    }

    @Transactional
    public boolean splitTransaction(String transactionId, List<SplitAllocation> allocations) {
        Optional<TransactionBancaire> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty() || allocations == null || allocations.isEmpty()) {
            return false;
        }

        TransactionBancaire transaction = transactionOpt.get();
        double montantTransaction = getMontantTransaction(transaction);
        double totalSplit = allocations.stream().mapToDouble(a -> nz(a.getMontant())).sum();
        if (Math.abs(montantTransaction - totalSplit) > 0.01) {
            return false;
        }

        for (SplitAllocation allocation : allocations) {
            Paiement paiement = creerPaiementSplit(transaction, allocation);
            if (paiement == null) {
                throw new IllegalStateException("Impossible de créer un paiement pour le split");
            }
        }

        transaction.setMapped(true);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        return true;
    }

    private Paiement creerPaiementSplit(TransactionBancaire transaction, SplitAllocation allocation) {
        if (allocation == null || allocation.getMontant() == null || allocation.getMontant() <= 0) {
            return null;
        }
        if ((allocation.getFactureVenteId() == null || allocation.getFactureVenteId().isBlank())
                && (allocation.getFactureAchatId() == null || allocation.getFactureAchatId().isBlank())) {
            return null;
        }

        Paiement.PaiementBuilder builder = Paiement.builder()
                .date(transaction.getDateOperation())
                .montant(allocation.getMontant())
                .reference(transaction.getReference())
                .transactionBancaireId(transaction.getId())
                .mode(determinerModePaiement(transaction.getLibelle()));

        if (allocation.getFactureVenteId() != null && !allocation.getFactureVenteId().isBlank()) {
            FactureVente facture = factureVenteRepository.findById(allocation.getFactureVenteId()).orElse(null);
            if (facture == null) return null;
            builder.factureVenteId(facture.getId()).typeMouvement("C").nature("paiement");
            if (facture.getTvaRate() != null) builder.tvaRate(facture.getTvaRate());
        } else {
            FactureAchat facture = factureAchatRepository.findById(allocation.getFactureAchatId()).orElse(null);
            if (facture == null) return null;
            builder.factureAchatId(facture.getId()).typeMouvement("F").nature("paiement");
            if (facture.getTvaRate() != null) builder.tvaRate(facture.getTvaRate());
        }

        return paiementService.create(builder.build());
    }

    private double mouvementBanqueEcriture(EcritureComptable ecriture) {
        if (ecriture.getLignes() == null) return 0.0;
        return ecriture.getLignes().stream()
                .filter(l -> "5141".equals(l.getCompteCode()))
                .mapToDouble(l -> nz(l.getDebit()) - nz(l.getCredit()))
                .sum();
    }

    private double getMontantTransaction(TransactionBancaire transaction) {
        return transaction.getCredit() != null && transaction.getCredit() > 0
                ? transaction.getCredit()
                : (transaction.getDebit() != null && transaction.getDebit() > 0 ? transaction.getDebit() : 0.0);
    }

    private double nz(Double value) {
        return value != null ? value : 0.0;
    }

    @Data
    public static class SplitAllocation {
        private String factureVenteId;
        private String factureAchatId;
        private Double montant;
    }
}

