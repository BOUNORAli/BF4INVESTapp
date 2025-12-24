package com.bf4invest.controller;

import com.bf4invest.model.CompteComptable;
import com.bf4invest.model.EcritureComptable;
import com.bf4invest.model.ExerciceComptable;
import com.bf4invest.service.ComptabiliteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/comptabilite")
@RequiredArgsConstructor
public class ComptabiliteController {

    private final ComptabiliteService comptabiliteService;
    private final com.bf4invest.excel.ExcelExportService excelExportService;

    // ========== COMPTES COMPTABLES ==========

    @GetMapping("/comptes")
    public ResponseEntity<List<CompteComptable>> getAllComptes() {
        try {
            return ResponseEntity.ok(comptabiliteService.getAllComptesActifs());
        } catch (Exception e) {
            log.error("Erreur lors du chargement des comptes", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/comptes/{code}")
    public ResponseEntity<CompteComptable> getCompteByCode(@PathVariable String code) {
        Optional<CompteComptable> compte = comptabiliteService.getCompteByCode(code);
        return compte.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== EXERCICES COMPTABLES ==========

    @GetMapping("/exercices")
    public ResponseEntity<List<ExerciceComptable>> getAllExercices() {
        try {
            return ResponseEntity.ok(comptabiliteService.getAllExercices());
        } catch (Exception e) {
            log.error("Erreur lors du chargement des exercices", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/exercices/current")
    public ResponseEntity<ExerciceComptable> getCurrentExercice() {
        try {
            return ResponseEntity.ok(comptabiliteService.getOrCreateCurrentExercice());
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'exercice courant", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/exercices")
    public ResponseEntity<ExerciceComptable> createExercice(@RequestBody ExerciceComptable exercice) {
        try {
            // TODO: Implémenter la création d'exercice dans le service
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (Exception e) {
            log.error("Erreur lors de la création de l'exercice", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== ÉCRITURES COMPTABLES ==========

    @GetMapping("/ecritures")
    public ResponseEntity<List<EcritureComptable>> getEcritures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String journal,
            @RequestParam(required = false) String exerciceId,
            @RequestParam(required = false) String pieceType,
            @RequestParam(required = false) String pieceId
    ) {
        try {
            return ResponseEntity.ok(comptabiliteService.getEcritures(dateDebut, dateFin, journal, exerciceId, pieceType, pieceId));
        } catch (Exception e) {
            log.error("Erreur lors du chargement des écritures", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/ecritures/{id}")
    public ResponseEntity<EcritureComptable> getEcriture(@PathVariable String id) {
        return comptabiliteService.getEcritureById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== GRAND LIVRE ==========

    @GetMapping("/grand-livre")
    public ResponseEntity<List<EcritureComptable>> getGrandLivre(
            @RequestParam String compteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            return ResponseEntity.ok(comptabiliteService.getGrandLivre(compteCode, dateDebut, dateFin, exerciceId));
        } catch (Exception e) {
            log.error("Erreur lors du chargement du grand livre", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== BALANCE ==========

    @GetMapping("/balance")
    public ResponseEntity<List<CompteComptable>> getBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            return ResponseEntity.ok(comptabiliteService.getBalance(dateDebut, dateFin, exerciceId));
        } catch (Exception e) {
            log.error("Erreur lors du calcul de la balance", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== BILAN ==========

    @GetMapping("/bilan")
    public ResponseEntity<Map<String, Object>> getBilan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            return ResponseEntity.ok(comptabiliteService.getBilan(date, exerciceId));
        } catch (Exception e) {
            log.error("Erreur lors du calcul du bilan", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== CPC ==========

    @GetMapping("/cpc")
    public ResponseEntity<Map<String, Object>> getCPC(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            return ResponseEntity.ok(comptabiliteService.getCPC(dateDebut, dateFin, exerciceId));
        } catch (Exception e) {
            log.error("Erreur lors du calcul du CPC", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== EXPORTS ==========

    @GetMapping("/export/journal")
    public ResponseEntity<byte[]> exportJournal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId,
            @RequestParam(required = false) String pieceType,
            @RequestParam(required = false) String pieceId
    ) {
        try {
            List<com.bf4invest.model.EcritureComptable> ecritures = comptabiliteService.getEcritures(dateDebut, dateFin, null, exerciceId, pieceType, pieceId);
            byte[] excel = excelExportService.exportJournalComptable(ecritures);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String fileName = pieceType != null && pieceId != null ? 
                "journal_" + pieceType + "_" + pieceId + ".xlsx" : 
                "journal_comptable.xlsx";
            headers.setContentDispositionFormData("attachment", fileName);
            
            return ResponseEntity.ok().headers(headers).body(excel);
        } catch (Exception e) {
            log.error("Erreur lors de l'export du journal", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/balance")
    public ResponseEntity<byte[]> exportBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            List<com.bf4invest.model.CompteComptable> balance = comptabiliteService.getBalance(dateDebut, dateFin, exerciceId);
            byte[] excel = excelExportService.exportBalance(balance);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "balance.xlsx");
            
            return ResponseEntity.ok().headers(headers).body(excel);
        } catch (Exception e) {
            log.error("Erreur lors de l'export de la balance", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/grand-livre")
    public ResponseEntity<byte[]> exportGrandLivre(
            @RequestParam String compteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            List<com.bf4invest.model.EcritureComptable> ecritures = comptabiliteService.getGrandLivre(compteCode, dateDebut, dateFin, exerciceId);
            byte[] excel = excelExportService.exportGrandLivre(ecritures, compteCode);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "grand_livre_" + compteCode + ".xlsx");
            
            return ResponseEntity.ok().headers(headers).body(excel);
        } catch (Exception e) {
            log.error("Erreur lors de l'export du grand livre", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== INITIALISATION ==========

    @PostMapping("/init")
    public ResponseEntity<Void> initializePlanComptable() {
        try {
            comptabiliteService.initializePlanComptable();
            comptabiliteService.getOrCreateCurrentExercice();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du plan comptable", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== RÉGÉNÉRATION DES ÉCRITURES ==========

    @PostMapping("/regenerer-ecritures")
    public ResponseEntity<Map<String, Integer>> regenererEcrituresManquantes() {
        try {
            Map<String, Integer> result = comptabiliteService.regenererEcrituresManquantes();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur lors de la régénération des écritures", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

