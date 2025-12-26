package com.bf4invest.controller;

import com.bf4invest.model.DeclarationTVA;
import com.bf4invest.service.TVAService;
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
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/tva")
@RequiredArgsConstructor
public class TVAController {

    private final TVAService tvaService;
    private final com.bf4invest.excel.ExcelExportService excelExportService;

    @GetMapping("/declarations")
    public ResponseEntity<List<DeclarationTVA>> getDeclarations(
            @RequestParam(required = false) Integer annee
    ) {
        try {
            if (annee != null) {
                return ResponseEntity.ok(tvaService.getDeclarationsByAnnee(annee));
            }
            // Par défaut, année en cours
            int currentYear = LocalDate.now().getYear();
            return ResponseEntity.ok(tvaService.getDeclarationsByAnnee(currentYear));
        } catch (Exception e) {
            log.error("Erreur lors du chargement des déclarations TVA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/declarations/{mois}/{annee}")
    public ResponseEntity<DeclarationTVA> getDeclaration(
            @PathVariable Integer mois,
            @PathVariable Integer annee
    ) {
        Optional<DeclarationTVA> declaration = tvaService.getDeclarationByMoisAnnee(mois, annee);
        return declaration.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/declarations/calculer")
    public ResponseEntity<DeclarationTVA> calculerDeclaration(
            @RequestParam Integer mois,
            @RequestParam Integer annee
    ) {
        try {
            // Utiliser le calcul au règlement (basé sur les paiements)
            DeclarationTVA declaration = tvaService.calculerDeclarationTVAAuReglement(mois, annee);
            return ResponseEntity.ok(declaration);
        } catch (Exception e) {
            log.error("Erreur lors du calcul de la déclaration TVA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/declarations/{id}/valider")
    public ResponseEntity<DeclarationTVA> validerDeclaration(@PathVariable String id) {
        try {
            return ResponseEntity.ok(tvaService.validerDeclaration(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la validation de la déclaration TVA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/declarations/{id}/deposer")
    public ResponseEntity<DeclarationTVA> deposerDeclaration(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDepot
    ) {
        try {
            return ResponseEntity.ok(tvaService.marquerDeposee(id, dateDepot));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors du dépôt de la déclaration TVA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/declarations")
    public ResponseEntity<byte[]> exportDeclarations(
            @RequestParam(required = false) Integer annee
    ) {
        try {
            List<DeclarationTVA> declarations;
            if (annee != null) {
                declarations = tvaService.getDeclarationsByAnnee(annee);
            } else {
                int currentYear = LocalDate.now().getYear();
                declarations = tvaService.getDeclarationsByAnnee(currentYear);
            }
            
            byte[] excel = excelExportService.exportDeclarationsTVA(declarations);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "declarations_tva.xlsx");
            
            return ResponseEntity.ok().headers(headers).body(excel);
        } catch (Exception e) {
            log.error("Erreur lors de l'export des déclarations TVA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/etat-detaille")
    public ResponseEntity<byte[]> exportEtatTVADetaille(
            @RequestParam Integer mois,
            @RequestParam Integer annee
    ) {
        try {
            byte[] excel = excelExportService.exportEtatTVADetaille(mois, annee);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String moisAbrev = getMoisAbreviation(mois);
            String filename = String.format("etat_tva_%s_%d.xlsx", moisAbrev, annee);
            headers.setContentDispositionFormData("attachment", filename);
            
            return ResponseEntity.ok().headers(headers).body(excel);
        } catch (Exception e) {
            log.error("Erreur lors de l'export de l'état TVA détaillé pour {}/{}", mois, annee, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String getMoisAbreviation(Integer mois) {
        String[] moisAbrev = {"janv", "fevr", "mars", "avr", "mai", "juin",
                "juil", "aout", "sept", "oct", "nov", "dec"};
        if (mois >= 1 && mois <= 12) {
            return moisAbrev[mois - 1];
        }
        return "janv";
    }
}

