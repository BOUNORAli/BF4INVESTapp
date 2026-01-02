package com.bf4invest.service;

import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.OrdreVirement;
import com.bf4invest.model.Paiement;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.OrdreVirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdreVirementService {
    
    private final OrdreVirementRepository repository;
    private final FactureAchatRepository factureAchatRepository;
    private final SupplierService supplierService;
    private final AuditService auditService;
    private final PaiementService paiementService;
    
    public List<OrdreVirement> findAll() {
        return repository.findAll();
    }
    
    public Optional<OrdreVirement> findById(String id) {
        return repository.findById(id);
    }
    
    public OrdreVirement create(OrdreVirement ov) {
        // Générer le numéro si non fourni
        if (ov.getNumeroOV() == null || ov.getNumeroOV().isEmpty()) {
            ov.setNumeroOV(generateOVNumber(ov.getDateOV()));
        }
        
        // Valider que les factures existent
        if (ov.getFacturesIds() != null && !ov.getFacturesIds().isEmpty()) {
            validateFactures(ov.getFacturesIds());
        }
        
        // Valider facturesMontants si présent
        if (ov.getFacturesMontants() != null && !ov.getFacturesMontants().isEmpty()) {
            for (OrdreVirement.FactureMontant fm : ov.getFacturesMontants()) {
                validateFactures(java.util.Collections.singletonList(fm.getFactureId()));
                if (fm.getMontant() == null || fm.getMontant() <= 0) {
                    throw new IllegalArgumentException("Le montant doit être supérieur à 0 pour la facture: " + fm.getFactureId());
                }
            }
        }
        
        // Calculer le montant total si facturesMontants est fourni
        if (ov.getFacturesMontants() != null && !ov.getFacturesMontants().isEmpty()) {
            double total = ov.getFacturesMontants().stream()
                    .mapToDouble(OrdreVirement.FactureMontant::getMontant)
                    .sum();
            ov.setMontant(total);
        }
        
        // Récupérer et dénormaliser le nom du bénéficiaire et le RIB
        if (ov.getBeneficiaireId() != null && !ov.getBeneficiaireId().isEmpty()) {
            Supplier supplier = supplierService.findById(ov.getBeneficiaireId())
                    .orElseThrow(() -> new IllegalArgumentException("Fournisseur non trouvé: " + ov.getBeneficiaireId()));
            ov.setNomBeneficiaire(supplier.getNom());
            // Auto-remplir le RIB si non fourni et disponible dans le fournisseur
            if ((ov.getRibBeneficiaire() == null || ov.getRibBeneficiaire().isEmpty()) && supplier.getRib() != null) {
                ov.setRibBeneficiaire(supplier.getRib());
            }
            // Auto-remplir la banque bénéficiaire si non fournie et disponible dans le fournisseur
            if ((ov.getBanqueBeneficiaire() == null || ov.getBanqueBeneficiaire().isEmpty()) && supplier.getBanque() != null) {
                ov.setBanqueBeneficiaire(supplier.getBanque());
            }
        } else {
            // Si beneficiaireId est null (personne physique), valider que nomBeneficiaire est fourni
            if (ov.getNomBeneficiaire() == null || ov.getNomBeneficiaire().trim().isEmpty()) {
                throw new IllegalArgumentException("Le nom du bénéficiaire est requis pour une personne physique");
            }
            // Préserver le nomBeneficiaire fourni (déjà dans l'objet ov)
            // Ne rien faire, le nomBeneficiaire est déjà présent
        }
        
        // Initialiser le statut si non fourni
        if (ov.getStatut() == null || ov.getStatut().isEmpty()) {
            ov.setStatut("EN_ATTENTE");
        }
        
        // Initialiser le type si non fourni
        if (ov.getType() == null || ov.getType().isEmpty()) {
            ov.setType("NORMAL");
        }
        
        // Construire facturesIds à partir de facturesMontants si nécessaire
        if (ov.getFacturesMontants() != null && !ov.getFacturesMontants().isEmpty()) {
            if (ov.getFacturesIds() == null || ov.getFacturesIds().isEmpty()) {
                ov.setFacturesIds(ov.getFacturesMontants().stream()
                        .map(OrdreVirement.FactureMontant::getFactureId)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
        
        ov.setCreatedAt(LocalDateTime.now());
        ov.setUpdatedAt(LocalDateTime.now());
        
        OrdreVirement saved = repository.save(ov);
        
        // Journaliser la création
        String facturesInfo = saved.getFacturesIds() != null && !saved.getFacturesIds().isEmpty() 
                ? " (" + saved.getFacturesIds().size() + " facture(s))" : "";
        auditService.logCreate("OrdreVirement", saved.getId(), 
            "Ordre de virement " + saved.getNumeroOV() + " créé - Montant: " + saved.getMontant() + " MAD" + facturesInfo);
        
        return saved;
    }
    
    public OrdreVirement update(String id, OrdreVirement ov) {
        return repository.findById(id)
                .map(existing -> {
                    String oldStatut = existing.getStatut();
                    
                    // Valider les champs requis
                    if (ov.getDateOV() == null) {
                        throw new IllegalArgumentException("La date OV est requise");
                    }
                    if (ov.getMontant() == null || ov.getMontant() <= 0) {
                        throw new IllegalArgumentException("Le montant doit être supérieur à 0");
                    }
                    if (ov.getRibBeneficiaire() == null || ov.getRibBeneficiaire().trim().isEmpty()) {
                        throw new IllegalArgumentException("Le RIB bénéficiaire est requis");
                    }
                    if (ov.getBanqueBeneficiaire() == null || ov.getBanqueBeneficiaire().trim().isEmpty()) {
                        throw new IllegalArgumentException("La banque bénéficiaire est requise");
                    }
                    if (ov.getBanqueEmettrice() == null || ov.getBanqueEmettrice().trim().isEmpty()) {
                        throw new IllegalArgumentException("La banque émettrice est requise");
                    }
                    if (ov.getMotif() == null || ov.getMotif().trim().isEmpty()) {
                        throw new IllegalArgumentException("Le motif est requis");
                    }
                    
                    existing.setDateOV(ov.getDateOV());
                    existing.setMontant(ov.getMontant());
                    // Normaliser beneficiaireId : chaîne vide devient null
                    existing.setBeneficiaireId(ov.getBeneficiaireId() != null && !ov.getBeneficiaireId().trim().isEmpty() 
                        ? ov.getBeneficiaireId() : null);
                    existing.setRibBeneficiaire(ov.getRibBeneficiaire());
                    existing.setBanqueBeneficiaire(ov.getBanqueBeneficiaire());
                    existing.setMotif(ov.getMotif());
                    existing.setFacturesIds(ov.getFacturesIds());
                    existing.setFacturesMontants(ov.getFacturesMontants());
                    existing.setBanqueEmettrice(ov.getBanqueEmettrice());
                    existing.setDateExecution(ov.getDateExecution());
                    
                    // Mettre à jour le type si fourni
                    if (ov.getType() != null && !ov.getType().isEmpty()) {
                        existing.setType(ov.getType());
                    }
                    
                    // Mettre à jour le statut si fourni
                    if (ov.getStatut() != null && !ov.getStatut().isEmpty()) {
                        existing.setStatut(ov.getStatut());
                    }
                    
                    // Valider que les factures existent
                    if (existing.getFacturesIds() != null && !existing.getFacturesIds().isEmpty()) {
                        validateFactures(existing.getFacturesIds());
                    }
                    
                    // Récupérer et dénormaliser le nom du bénéficiaire et le RIB
                    if (existing.getBeneficiaireId() != null && !existing.getBeneficiaireId().isEmpty()) {
                        Supplier supplier = supplierService.findById(existing.getBeneficiaireId())
                                .orElseThrow(() -> new IllegalArgumentException("Fournisseur non trouvé: " + existing.getBeneficiaireId()));
                        existing.setNomBeneficiaire(supplier.getNom());
                        // Auto-remplir le RIB si non fourni et disponible dans le fournisseur
                        if ((existing.getRibBeneficiaire() == null || existing.getRibBeneficiaire().isEmpty()) && supplier.getRib() != null) {
                            existing.setRibBeneficiaire(supplier.getRib());
                        }
                        // Auto-remplir la banque bénéficiaire si non fournie et disponible dans le fournisseur
                        if ((existing.getBanqueBeneficiaire() == null || existing.getBanqueBeneficiaire().isEmpty()) && supplier.getBanque() != null) {
                            existing.setBanqueBeneficiaire(supplier.getBanque());
                        }
                    } else {
                        // Si beneficiaireId est null (personne physique), valider et mettre à jour le nomBeneficiaire
                        if (ov.getNomBeneficiaire() != null && !ov.getNomBeneficiaire().trim().isEmpty()) {
                            existing.setNomBeneficiaire(ov.getNomBeneficiaire().trim());
                        } else if (existing.getNomBeneficiaire() == null || existing.getNomBeneficiaire().trim().isEmpty()) {
                            // Si le nomBeneficiaire n'est ni fourni ni existant, générer une erreur
                            throw new IllegalArgumentException("Le nom du bénéficiaire est requis pour une personne physique");
                        }
                        // Sinon, conserver le nomBeneficiaire existant (pas de changement)
                    }
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    
                    OrdreVirement saved = repository.save(existing);
                    
                    // Journaliser la mise à jour
                    if (ov.getStatut() != null && !ov.getStatut().equals(oldStatut)) {
                        auditService.logUpdate("OrdreVirement", id, 
                            "Statut: " + oldStatut, "Statut: " + saved.getStatut());
                    } else {
                        auditService.logUpdate("OrdreVirement", id, 
                            "Ordre de virement " + saved.getNumeroOV() + " modifié", 
                            "Ordre de virement " + saved.getNumeroOV() + " modifié");
                    }
                    
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Ordre de virement non trouvé: " + id));
    }
    
    public void delete(String id) {
        repository.findById(id).ifPresent(ov -> {
            auditService.logDelete("OrdreVirement", id, "Ordre de virement " + ov.getNumeroOV() + " supprimé");
        });
        repository.deleteById(id);
    }
    
    public OrdreVirement marquerExecute(String id) {
        return repository.findById(id)
                .map(ov -> {
                    String oldStatut = ov.getStatut();
                    
                    // Vérifier si déjà EXECUTE (idempotence)
                    if ("EXECUTE".equals(oldStatut)) {
                        log.warn("Ordre de virement {} déjà EXECUTE, aucun nouveau paiement créé", ov.getNumeroOV());
                        return ov;
                    }
                    
                    ov.setStatut("EXECUTE");
                    ov.setUpdatedAt(LocalDateTime.now());
                    OrdreVirement saved = repository.save(ov);
                    
                    // Créer automatiquement les paiements pour les factures liées
                    creerPaiementsPourOV(saved);
                    
                    auditService.logUpdate("OrdreVirement", id, 
                        "Statut: " + oldStatut, "Statut: EXECUTE");
                    
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Ordre de virement non trouvé: " + id));
    }
    
    /**
     * Crée automatiquement des paiements pour les factures achat liées à l'ordre de virement.
     * Utilise facturesMontants si disponible, sinon répartit le montant total entre facturesIds.
     * La logique FIFO des prévisions sera appliquée automatiquement via PaiementService.
     */
    private void creerPaiementsPourOV(OrdreVirement ov) {
        if (ov.getMontant() == null || ov.getMontant() <= 0) {
            log.warn("Ordre de virement {} n'a pas de montant valide, aucun paiement créé", ov.getNumeroOV());
            return;
        }
        
        // Date du paiement: dateExecution si disponible, sinon dateOV
        java.time.LocalDate datePaiement = ov.getDateExecution() != null ? ov.getDateExecution() : ov.getDateOV();
        if (datePaiement == null) {
            datePaiement = java.time.LocalDate.now();
        }
        
        int paiementsCrees = 0;
        int erreurs = 0;
        
        // Cas 1: facturesMontants existe (montants partiels explicites)
        if (ov.getFacturesMontants() != null && !ov.getFacturesMontants().isEmpty()) {
            for (OrdreVirement.FactureMontant fm : ov.getFacturesMontants()) {
                try {
                    // Vérifier que la facture existe
                    Optional<FactureAchat> factureOpt = factureAchatRepository.findById(fm.getFactureId());
                    if (factureOpt.isEmpty()) {
                        log.error("Facture {} non trouvée pour l'ordre de virement {}", fm.getFactureId(), ov.getNumeroOV());
                        erreurs++;
                        continue;
                    }
                    
                    if (fm.getMontant() == null || fm.getMontant() <= 0) {
                        log.warn("Montant invalide pour facture {} dans ordre de virement {}", fm.getFactureId(), ov.getNumeroOV());
                        erreurs++;
                        continue;
                    }
                    
                    // Créer le paiement
                    Paiement paiement = Paiement.builder()
                            .factureAchatId(fm.getFactureId())
                            .montant(fm.getMontant())
                            .date(datePaiement)
                            .mode("virement")
                            .reference(ov.getNumeroOV())
                            .nature("paiement")
                            .typeMouvement("F") // Fournisseur
                            .build();
                    
                    paiementService.create(paiement);
                    paiementsCrees++;
                    log.info("Paiement créé pour facture {} (montant: {} MAD) via ordre de virement {}", 
                            fm.getFactureId(), fm.getMontant(), ov.getNumeroOV());
                    
                } catch (Exception e) {
                    log.error("Erreur lors de la création du paiement pour facture {} dans ordre de virement {}: {}", 
                            fm.getFactureId(), ov.getNumeroOV(), e.getMessage(), e);
                    erreurs++;
                }
            }
        }
        // Cas 2: facturesIds existe mais pas facturesMontants (répartition équitable)
        else if (ov.getFacturesIds() != null && !ov.getFacturesIds().isEmpty()) {
            int nbFactures = ov.getFacturesIds().size();
            double montantParFacture = ov.getMontant() / nbFactures;
            
            for (String factureId : ov.getFacturesIds()) {
                try {
                    // Vérifier que la facture existe
                    Optional<FactureAchat> factureOpt = factureAchatRepository.findById(factureId);
                    if (factureOpt.isEmpty()) {
                        log.error("Facture {} non trouvée pour l'ordre de virement {}", factureId, ov.getNumeroOV());
                        erreurs++;
                        continue;
                    }
                    
                    // Créer le paiement avec montant réparti
                    Paiement paiement = Paiement.builder()
                            .factureAchatId(factureId)
                            .montant(montantParFacture)
                            .date(datePaiement)
                            .mode("virement")
                            .reference(ov.getNumeroOV())
                            .nature("paiement")
                            .typeMouvement("F") // Fournisseur
                            .build();
                    
                    paiementService.create(paiement);
                    paiementsCrees++;
                    log.info("Paiement créé pour facture {} (montant: {} MAD, réparti) via ordre de virement {}", 
                            factureId, montantParFacture, ov.getNumeroOV());
                    
                } catch (Exception e) {
                    log.error("Erreur lors de la création du paiement pour facture {} dans ordre de virement {}: {}", 
                            factureId, ov.getNumeroOV(), e.getMessage(), e);
                    erreurs++;
                }
            }
        } else {
            log.warn("Ordre de virement {} n'a pas de factures associées, aucun paiement créé", ov.getNumeroOV());
        }
        
        if (paiementsCrees > 0) {
            log.info("{} paiement(s) créé(s) pour l'ordre de virement {}", paiementsCrees, ov.getNumeroOV());
        }
        if (erreurs > 0) {
            log.warn("{} erreur(s) lors de la création des paiements pour l'ordre de virement {}", erreurs, ov.getNumeroOV());
        }
    }
    
    public OrdreVirement marquerAnnule(String id) {
        return repository.findById(id)
                .map(ov -> {
                    String oldStatut = ov.getStatut();
                    ov.setStatut("ANNULE");
                    ov.setUpdatedAt(LocalDateTime.now());
                    OrdreVirement saved = repository.save(ov);
                    
                    auditService.logUpdate("OrdreVirement", id, 
                        "Statut: " + oldStatut, "Statut: ANNULE");
                    
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Ordre de virement non trouvé: " + id));
    }
    
    private String generateOVNumber(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("La date OV est requise pour générer le numéro");
        }
        
        String year = String.valueOf(date.getYear());
        long count = repository.findAll().stream()
                .filter(ov -> ov.getDateOV() != null && ov.getDateOV().getYear() == date.getYear())
                .count();
        String sequence = String.format("%03d", count + 1);
        return String.format("OV-%s-%s", year, sequence);
    }
    
    private void validateFactures(List<String> facturesIds) {
        for (String factureId : facturesIds) {
            Optional<FactureAchat> facture = factureAchatRepository.findById(factureId);
            if (facture.isEmpty()) {
                throw new IllegalArgumentException("Facture non trouvée: " + factureId);
            }
        }
    }
}

