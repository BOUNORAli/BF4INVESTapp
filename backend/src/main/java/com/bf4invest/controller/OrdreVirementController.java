package com.bf4invest.controller;

import com.bf4invest.model.OrdreVirement;
import com.bf4invest.service.OrdreVirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/ordres-virement")
@RequiredArgsConstructor
public class OrdreVirementController {
    
    private final OrdreVirementService service;
    
    @GetMapping
    public ResponseEntity<List<OrdreVirement>> getAll(
            @RequestParam(required = false) String beneficiaireId,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        List<OrdreVirement> ordres = service.findAll();
        
        // Filtrer si nécessaire
        if (beneficiaireId != null) {
            ordres = ordres.stream()
                    .filter(ov -> beneficiaireId.equals(ov.getBeneficiaireId()))
                    .toList();
        }
        if (statut != null) {
            ordres = ordres.stream()
                    .filter(ov -> statut.equals(ov.getStatut()))
                    .toList();
        }
        if (dateDebut != null) {
            ordres = ordres.stream()
                    .filter(ov -> ov.getDateOV() != null && !ov.getDateOV().isBefore(dateDebut))
                    .toList();
        }
        if (dateFin != null) {
            ordres = ordres.stream()
                    .filter(ov -> ov.getDateOV() != null && !ov.getDateOV().isAfter(dateFin))
                    .toList();
        }
        
        return ResponseEntity.ok(ordres);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<OrdreVirement> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<OrdreVirement> create(@RequestBody OrdreVirement ov) {
        try {
            OrdreVirement created = service.create(ov);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<OrdreVirement> update(@PathVariable String id, @RequestBody OrdreVirement ov) {
        try {
            OrdreVirement updated = service.update(id, ov);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/{id}/executer")
    public ResponseEntity<OrdreVirement> executer(@PathVariable String id) {
        try {
            OrdreVirement updated = service.marquerExecute(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/{id}/annuler")
    public ResponseEntity<OrdreVirement> annuler(@PathVariable String id) {
        try {
            OrdreVirement updated = service.marquerAnnule(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

