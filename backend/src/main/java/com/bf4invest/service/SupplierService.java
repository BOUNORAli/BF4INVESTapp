package com.bf4invest.service;

import com.bf4invest.model.Supplier;
import com.bf4invest.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SupplierService {
    
    private final SupplierRepository supplierRepository;
    private final AuditService auditService;
    
    public List<Supplier> findAll() {
        return supplierRepository.findAll();
    }
    
    public Optional<Supplier> findById(String id) {
        return supplierRepository.findById(id);
    }
    
    public Supplier create(Supplier supplier) {
        // Générer la référence si non fournie
        if (supplier.getReferenceFournisseur() == null || supplier.getReferenceFournisseur().trim().isEmpty()) {
            supplier.setReferenceFournisseur(generateReferenceFromName(supplier.getNom()));
        } else {
            // Normaliser la référence fournie
            supplier.setReferenceFournisseur(normalizeReference(supplier.getReferenceFournisseur()));
        }
        
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        Supplier saved = supplierRepository.save(supplier);
        
        // Journaliser la création
        auditService.logCreate("Fournisseur", saved.getId(), "Fournisseur " + saved.getNom() + " créé");
        
        return saved;
    }
    
    public Supplier update(String id, Supplier supplier) {
        return supplierRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getNom();
                    existing.setNom(supplier.getNom());
                    existing.setIce(supplier.getIce());
                    existing.setContact(supplier.getContact());
                    existing.setAdresse(supplier.getAdresse());
                    existing.setTelephone(supplier.getTelephone());
                    existing.setEmail(supplier.getEmail());
                    existing.setBanque(supplier.getBanque());
                    existing.setRib(supplier.getRib());
                    existing.setModesPaiementAcceptes(supplier.getModesPaiementAcceptes());
                    existing.setDateRegulariteFiscale(supplier.getDateRegulariteFiscale());
                    
                    // Gérer la référence
                    if (supplier.getReferenceFournisseur() != null && !supplier.getReferenceFournisseur().trim().isEmpty()) {
                        existing.setReferenceFournisseur(normalizeReference(supplier.getReferenceFournisseur()));
                    } else if (existing.getReferenceFournisseur() == null || existing.getReferenceFournisseur().trim().isEmpty()) {
                        // Générer si toujours vide
                        existing.setReferenceFournisseur(generateReferenceFromName(supplier.getNom()));
                    }
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    Supplier saved = supplierRepository.save(existing);
                    
                    // Journaliser la modification
                    auditService.logUpdate("Fournisseur", saved.getId(), oldName, "Fournisseur " + saved.getNom() + " modifié");
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
    }
    
    public void delete(String id) {
        // Journaliser avant suppression
        supplierRepository.findById(id).ifPresent(s -> {
            auditService.logDelete("Fournisseur", id, "Fournisseur " + s.getNom() + " supprimé");
        });
        supplierRepository.deleteById(id);
    }
    
    /**
     * Génère une référence à partir des 3 premières lettres du nom
     */
    private String generateReferenceFromName(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            return "XXX";
        }
        
        // Supprimer les accents et convertir en majuscules
        String normalized = normalizeReference(nom);
        
        // Prendre les 3 premiers caractères (lettres uniquement)
        StringBuilder ref = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (Character.isLetter(c)) {
                ref.append(c);
                if (ref.length() >= 3) {
                    break;
                }
            }
        }
        
        // Si moins de 3 lettres, compléter avec X
        while (ref.length() < 3) {
            ref.append('X');
        }
        
        return ref.toString().substring(0, 3).toUpperCase();
    }
    
    /**
     * Normalise une référence : supprime accents, espaces, convertit en majuscules
     */
    private String normalizeReference(String reference) {
        if (reference == null) {
            return "";
        }
        
        // Supprimer les accents
        String normalized = Normalizer.normalize(reference, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        
        // Supprimer les espaces et caractères spéciaux, garder seulement lettres et chiffres
        normalized = normalized.replaceAll("[^a-zA-Z0-9]", "");
        
        return normalized.toUpperCase();
    }
}




