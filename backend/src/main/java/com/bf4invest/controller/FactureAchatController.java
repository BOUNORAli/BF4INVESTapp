package com.bf4invest.controller;

import com.bf4invest.model.FactureAchat;
import com.bf4invest.service.FactureAchatService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/factures-achats")
@RequiredArgsConstructor
public class FactureAchatController {
    
    private final FactureAchatService factureService;
    
    @GetMapping
    public ResponseEntity<List<FactureAchat>> getAllFactures(
            @RequestParam(required = false) String fournisseurId,
            @RequestParam(required = false) String etatPaiement
    ) {
        List<FactureAchat> factures = factureService.findAll();
        
        if (fournisseurId != null) {
            factures = factures.stream()
                    .filter(f -> f.getFournisseurId() != null && f.getFournisseurId().equals(fournisseurId))
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
    public ResponseEntity<FactureAchat> getFacture(@PathVariable String id) {
        return factureService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<FactureAchat> createFacture(@RequestBody FactureAchat facture) {
        FactureAchat created = factureService.create(facture);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<FactureAchat> updateFacture(@PathVariable String id, @RequestBody FactureAchat facture) {
        try {
            FactureAchat updated = factureService.update(id, facture);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFacture(@PathVariable String id) {
        factureService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    // ========== ENDPOINTS POUR GESTION DES AVOIRS ==========
    
    /**
     * Récupère tous les avoirs d'achat
     */
    @GetMapping("/avoir")
    public ResponseEntity<List<FactureAchat>> getAllAvoirs() {
        List<FactureAchat> avoirs = factureService.getAllAvoirs();
        return ResponseEntity.ok(avoirs);
    }
    
    /**
     * Récupère les avoirs liés à une facture d'origine
     */
    @GetMapping("/{factureId}/avoir")
    public ResponseEntity<List<FactureAchat>> getAvoirsByFacture(@PathVariable String factureId) {
        List<FactureAchat> avoirs = factureService.getAvoirsByFacture(factureId);
        return ResponseEntity.ok(avoirs);
    }
    
    /**
     * Lie un avoir à une facture d'origine
     */
    @PostMapping("/{avoirId}/lier-a-facture/{factureOrigineId}")
    public ResponseEntity<Void> linkAvoirToFacture(
            @PathVariable String avoirId,
            @PathVariable String factureOrigineId) {
        try {
            factureService.linkAvoirToFacture(avoirId, factureOrigineId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}




