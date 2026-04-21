package com.bf4invest.controller;

import com.bf4invest.dto.FacturerBonsLivraisonGroupesRequest;
import com.bf4invest.model.FactureVente;
import com.bf4invest.service.FactureVenteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
            @RequestParam(required = false) String etatPaiement,
            @RequestParam(required = false) String statut
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

        if (statut != null && !statut.isBlank()) {
            factures = factures.stream()
                    .filter(f -> statut.equals(f.getStatut()))
                    .toList();
        } else {
            // Ne pas exposer les BL seuls ni les BL absorbés par défaut (liste factures vente classique)
            factures = factures.stream()
                    .filter(f -> !"BL_SEUL".equals(f.getStatut()) && !"MERGE_DANS_FV".equals(f.getStatut()))
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
        try {
            FactureVente created = factureService.create(facture);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/bons-livraison")
    public ResponseEntity<FactureVente> createBonLivraison(@RequestBody FactureVente payload) {
        try {
            FactureVente created = factureService.createBonLivraison(payload);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("createBonLivraison: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/bons-livraison/facturer-groupes")
    public ResponseEntity<FactureVente> facturerBonsLivraisonGroupes(@RequestBody FacturerBonsLivraisonGroupesRequest body) {
        try {
            if (body == null || body.getBlIds() == null) {
                return ResponseEntity.badRequest().build();
            }
            FactureVente created = factureService.facturerBonsLivraisonGroupes(body.getBlIds(), body.getDateFacture());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("facturerBonsLivraisonGroupes: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.warn("facturerBonsLivraisonGroupes: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/bons-livraison/{id}/facturer")
    public ResponseEntity<FactureVente> facturerBonLivraison(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate date) {
        LocalDate dateFacture = date != null ? date : LocalDate.now();
        try {
            FactureVente updated = factureService.facturerBonLivraison(id, dateFacture);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("facturerBonLivraison: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
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
        try {
            factureService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    // ========== ENDPOINTS POUR GESTION DES AVOIRS ==========
    
    /**
     * Récupère tous les avoirs de vente
     */
    @GetMapping("/avoir")
    public ResponseEntity<List<FactureVente>> getAllAvoirs() {
        List<FactureVente> avoirs = factureService.getAllAvoirs();
        return ResponseEntity.ok(avoirs);
    }
    
    /**
     * Récupère les avoirs liés à une facture d'origine
     */
    @GetMapping("/{factureId}/avoir")
    public ResponseEntity<List<FactureVente>> getAvoirsByFacture(@PathVariable String factureId) {
        List<FactureVente> avoirs = factureService.getAvoirsByFacture(factureId);
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




