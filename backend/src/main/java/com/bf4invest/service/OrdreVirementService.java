package com.bf4invest.service;

import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.OrdreVirement;
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
        
        // Récupérer et dénormaliser le nom du bénéficiaire
        if (ov.getBeneficiaireId() != null) {
            Supplier supplier = supplierService.findById(ov.getBeneficiaireId())
                    .orElseThrow(() -> new IllegalArgumentException("Fournisseur non trouvé: " + ov.getBeneficiaireId()));
            ov.setNomBeneficiaire(supplier.getNom());
        }
        
        // Initialiser le statut si non fourni
        if (ov.getStatut() == null || ov.getStatut().isEmpty()) {
            ov.setStatut("EN_ATTENTE");
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
                    
                    existing.setDateOV(ov.getDateOV());
                    existing.setMontant(ov.getMontant());
                    existing.setBeneficiaireId(ov.getBeneficiaireId());
                    existing.setRibBeneficiaire(ov.getRibBeneficiaire());
                    existing.setMotif(ov.getMotif());
                    existing.setFacturesIds(ov.getFacturesIds());
                    existing.setBanqueEmettrice(ov.getBanqueEmettrice());
                    existing.setDateExecution(ov.getDateExecution());
                    
                    // Mettre à jour le statut si fourni
                    if (ov.getStatut() != null && !ov.getStatut().isEmpty()) {
                        existing.setStatut(ov.getStatut());
                    }
                    
                    // Valider que les factures existent
                    if (existing.getFacturesIds() != null && !existing.getFacturesIds().isEmpty()) {
                        validateFactures(existing.getFacturesIds());
                    }
                    
                    // Récupérer et dénormaliser le nom du bénéficiaire
                    if (existing.getBeneficiaireId() != null) {
                        Supplier supplier = supplierService.findById(existing.getBeneficiaireId())
                                .orElseThrow(() -> new IllegalArgumentException("Fournisseur non trouvé: " + existing.getBeneficiaireId()));
                        existing.setNomBeneficiaire(supplier.getNom());
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
                    ov.setStatut("EXECUTE");
                    ov.setUpdatedAt(LocalDateTime.now());
                    OrdreVirement saved = repository.save(ov);
                    
                    auditService.logUpdate("OrdreVirement", id, 
                        "Statut: " + oldStatut, "Statut: EXECUTE");
                    
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Ordre de virement non trouvé: " + id));
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

