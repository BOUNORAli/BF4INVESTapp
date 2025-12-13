package com.bf4invest.service;

import com.bf4invest.model.Supplier;
import com.bf4invest.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                    existing.setAdresse(supplier.getAdresse());
                    existing.setTelephone(supplier.getTelephone());
                    existing.setEmail(supplier.getEmail());
                    existing.setModesPaiementAcceptes(supplier.getModesPaiementAcceptes());
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
}




