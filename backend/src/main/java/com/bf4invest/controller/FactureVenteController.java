package com.bf4invest.controller;

import com.bf4invest.model.FactureVente;
import com.bf4invest.service.FactureVenteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/factures-ventes")
@RequiredArgsConstructor
public class FactureVenteController {
    
    private final FactureVenteService factureService;
    
    @GetMapping
    public ResponseEntity<List<FactureVente>> getAllFactures(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String etatPaiement
    ) {
        List<FactureVente> factures = factureService.findAll();
        
        if (clientId != null) {
            factures = factures.stream()
                    .filter(f -> f.getClientId() != null && f.getClientId().equals(clientId))
                    .toList();
        }
        
        if (etatPaiement != null) {
            factures = factures.stream()
                    .filter(f -> f.getEtatPaiement() != null && f.getEtatPaiement().equals(etatPaiement))
                    .toList();
        }
        
        return ResponseEntity.ok(factures);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<FactureVente> getFacture(@PathVariable String id) {
        return factureService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<FactureVente> createFacture(@RequestBody FactureVente facture) {
        FactureVente created = factureService.create(facture);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<FactureVente> updateFacture(@PathVariable String id, @RequestBody FactureVente facture) {
        log.info("🔴 FactureVenteController.updateFacture - ID: {}", id);
        log.info("🔴 FactureVenteController.updateFacture - Facture reçue (toString): {}", facture);
        log.info("🔴 FactureVenteController.updateFacture - totalHT: {} (type: {}), totalTTC: {} (type: {})", 
            facture.getTotalHT(), 
            facture.getTotalHT() != null ? facture.getTotalHT().getClass().getSimpleName() : "null",
            facture.getTotalTTC(),
            facture.getTotalTTC() != null ? facture.getTotalTTC().getClass().getSimpleName() : "null");
        log.info("🔴 FactureVenteController.updateFacture - lignes: {}", 
            facture.getLignes() != null ? facture.getLignes().size() + " items" : "null");
        log.info("🔴 FactureVenteController.updateFacture - etatPaiement: {}", facture.getEtatPaiement());
        
        // Vérifier si les lignes sont présentes mais vides
        if (facture.getLignes() != null) {
            log.info("🔴 FactureVenteController.updateFacture - Lignes présentes (size={}), isEmpty={}", 
                facture.getLignes().size(), facture.getLignes().isEmpty());
        }
        
        try {
            FactureVente updated = factureService.update(id, facture);
            log.info("🔴 FactureVenteController.updateFacture - Facture mise à jour: totalHT={}, totalTTC={}", 
                updated.getTotalHT(), updated.getTotalTTC());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            log.error("❌ FactureVenteController.updateFacture - Erreur: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFacture(@PathVariable String id) {
        factureService.delete(id);
        return ResponseEntity.noContent().build();
    }
}




