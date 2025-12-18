package com.bf4invest.controller;

import com.bf4invest.model.HistoriqueSolde;
import com.bf4invest.model.SoldeGlobal;
import com.bf4invest.service.SoldeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/solde")
@RequiredArgsConstructor
public class SoldeController {
    
    private final SoldeService soldeService;
    
    @GetMapping("/global")
    public ResponseEntity<Double> getSoldeGlobalActuel() {
        Double solde = soldeService.getSoldeGlobalActuel();
        return ResponseEntity.ok(solde);
    }
    
    @GetMapping("/global/complet")
    public ResponseEntity<SoldeGlobal> getSoldeGlobal() {
        Optional<SoldeGlobal> solde = soldeService.getSoldeGlobal();
        return solde.map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(SoldeGlobal.builder()
                        .soldeInitial(0.0)
                        .soldeActuel(0.0)
                        .dateDebut(LocalDate.now())
                        .build()));
    }
    
    @PutMapping("/initial")
    public ResponseEntity<SoldeGlobal> initialiserSoldeDepart(
            @RequestParam Double montant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut
    ) {
        SoldeGlobal solde = soldeService.initialiserSoldeDepart(montant, dateDebut);
        return ResponseEntity.ok(solde);
    }
    
    @GetMapping("/partenaire/{type}/{id}")
    public ResponseEntity<Double> getSoldePartenaire(
            @PathVariable String type,
            @PathVariable String id
    ) {
        Double solde = soldeService.getSoldePartenaire(id, type.toUpperCase());
        return ResponseEntity.ok(solde);
    }
    
    @GetMapping("/historique")
    public ResponseEntity<List<HistoriqueSolde>> getHistorique(
            @RequestParam(required = false) String partenaireId,
            @RequestParam(required = false) String partenaireType,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFin
    ) {
        List<HistoriqueSolde> historique = soldeService.getHistorique(
                partenaireId,
                partenaireType,
                type,
                dateDebut,
                dateFin
        );
        return ResponseEntity.ok(historique);
    }
}

