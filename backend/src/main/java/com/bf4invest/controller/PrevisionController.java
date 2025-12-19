package com.bf4invest.controller;

import com.bf4invest.dto.PrevisionTresorerieResponse;
import com.bf4invest.model.PrevisionPaiement;
import com.bf4invest.service.FactureAchatService;
import com.bf4invest.service.FactureVenteService;
import com.bf4invest.service.PrevisionTresorerieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/prevision")
@RequiredArgsConstructor
public class PrevisionController {
    
    private final PrevisionTresorerieService previsionTresorerieService;
    private final FactureVenteService factureVenteService;
    private final FactureAchatService factureAchatService;
    
    @GetMapping("/tresorerie")
    public ResponseEntity<PrevisionTresorerieResponse> getPrevisionTresorerie(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            // Par défaut, période de 3 mois à partir d'aujourd'hui
            if (from == null) {
                from = LocalDate.now();
            }
            if (to == null) {
                to = from.plusMonths(3);
            }
            
            PrevisionTresorerieResponse response = previsionTresorerieService.getPrevisionTresorerie(from, to);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la prévision de trésorerie", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/facture-vente/{id}")
    public ResponseEntity<PrevisionPaiement> addPrevisionFactureVente(
            @PathVariable String id,
            @RequestBody PrevisionPaiement prevision) {
        try {
            PrevisionPaiement saved = factureVenteService.addPrevision(id, prevision);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (RuntimeException e) {
            log.error("Erreur lors de l'ajout de la prévision pour facture vente {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de l'ajout de la prévision", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/facture-achat/{id}")
    public ResponseEntity<PrevisionPaiement> addPrevisionFactureAchat(
            @PathVariable String id,
            @RequestBody PrevisionPaiement prevision) {
        try {
            PrevisionPaiement saved = factureAchatService.addPrevision(id, prevision);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (RuntimeException e) {
            log.error("Erreur lors de l'ajout de la prévision pour facture achat {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de l'ajout de la prévision", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{factureId}/{previsionId}")
    public ResponseEntity<PrevisionPaiement> updatePrevision(
            @PathVariable String factureId,
            @PathVariable String previsionId,
            @RequestParam String type, // "vente" ou "achat"
            @RequestBody PrevisionPaiement prevision) {
        try {
            PrevisionPaiement updated;
            if ("vente".equalsIgnoreCase(type)) {
                updated = factureVenteService.updatePrevision(factureId, previsionId, prevision);
            } else if ("achat".equalsIgnoreCase(type)) {
                updated = factureAchatService.updatePrevision(factureId, previsionId, prevision);
            } else {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            log.error("Erreur lors de la mise à jour de la prévision", e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour de la prévision", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{factureId}/{previsionId}")
    public ResponseEntity<Void> deletePrevision(
            @PathVariable String factureId,
            @PathVariable String previsionId,
            @RequestParam String type) { // "vente" ou "achat"
        try {
            if ("vente".equalsIgnoreCase(type)) {
                factureVenteService.deletePrevision(factureId, previsionId);
            } else if ("achat".equalsIgnoreCase(type)) {
                factureAchatService.deletePrevision(factureId, previsionId);
            } else {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Erreur lors de la suppression de la prévision", e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la suppression de la prévision", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

