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
    
    public List<Supplier> findAll() {
        return supplierRepository.findAll();
    }
    
    public Optional<Supplier> findById(String id) {
        return supplierRepository.findById(id);
    }
    
    public Supplier create(Supplier supplier) {
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(supplier);
    }
    
    public Supplier update(String id, Supplier supplier) {
        return supplierRepository.findById(id)
                .map(existing -> {
                    existing.setNom(supplier.getNom());
                    existing.setIce(supplier.getIce());
                    existing.setAdresse(supplier.getAdresse());
                    existing.setTelephone(supplier.getTelephone());
                    existing.setEmail(supplier.getEmail());
                    existing.setModesPaiementAcceptes(supplier.getModesPaiementAcceptes());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return supplierRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
    }
    
    public void delete(String id) {
        supplierRepository.deleteById(id);
    }
}




