package com.bf4invest.controller;

import com.bf4invest.model.Paiement;
import com.bf4invest.service.PaiementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/paiements")
@RequiredArgsConstructor
public class PaiementController {
    
    private final PaiementService paiementService;
    
    @PostMapping
    public ResponseEntity<Paiement> createPaiement(@RequestBody Paiement paiement) {
        Paiement created = paiementService.create(paiement);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Paiement> updatePaiement(@PathVariable String id, @RequestBody Paiement paiement) {
        Paiement updated = paiementService.update(id, paiement);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaiement(@PathVariable String id) {
        paiementService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping
    public ResponseEntity<List<Paiement>> getPaiements(
            @RequestParam(required = false) String factureAchatId,
            @RequestParam(required = false) String factureVenteId
    ) {
        if (factureAchatId != null) {
            return ResponseEntity.ok(paiementService.findByFactureAchatId(factureAchatId));
        }
        if (factureVenteId != null) {
            return ResponseEntity.ok(paiementService.findByFactureVenteId(factureVenteId));
        }
        return ResponseEntity.ok(List.of());
    }
}




