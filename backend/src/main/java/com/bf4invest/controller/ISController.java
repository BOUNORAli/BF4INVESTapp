package com.bf4invest.controller;

import com.bf4invest.model.AcompteIS;
import com.bf4invest.model.DeclarationIS;
import com.bf4invest.model.ISBaremeConfig;
import com.bf4invest.service.ISService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/is")
@RequiredArgsConstructor
public class ISController {

    private final ISService isService;

    public record CalculISRequest(
            Integer annee,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            String exerciceId,
            List<DeclarationIS.AjustementFiscal> reintegrations,
            List<DeclarationIS.AjustementFiscal> deductions
    ) {}

    public record PaiementAcompteRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePaiement,
            Double montantPaye
    ) {}

    @GetMapping("/calculer")
    public ResponseEntity<Map<String, Object>> calculerIS(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            return ResponseEntity.ok(isService.calculerIS(dateDebut, dateFin, exerciceId));
        } catch (Exception e) {
            log.error("Erreur lors du calcul de l'IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/declarations/calculer")
    public ResponseEntity<DeclarationIS> calculerDeclaration(@RequestBody CalculISRequest request) {
        try {
            DeclarationIS declaration = isService.calculerEtEnregistrerDeclaration(
                    request.annee(),
                    request.dateDebut(),
                    request.dateFin(),
                    request.exerciceId(),
                    request.reintegrations(),
                    request.deductions()
            );
            return ResponseEntity.ok(declaration);
        } catch (Exception e) {
            log.error("Erreur lors du calcul de la declaration IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/declarations")
    public ResponseEntity<List<DeclarationIS>> getDeclarations() {
        try {
            return ResponseEntity.ok(isService.getDeclarations());
        } catch (Exception e) {
            log.error("Erreur lors du chargement des declarations IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/declarations/{annee}")
    public ResponseEntity<DeclarationIS> getDeclaration(@PathVariable Integer annee) {
        return isService.getDeclarationByAnnee(annee)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/declarations/{annee}/valider")
    public ResponseEntity<DeclarationIS> validerDeclaration(@PathVariable Integer annee) {
        try {
            return ResponseEntity.ok(isService.validerDeclaration(annee));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la validation declaration IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/acomptes")
    public ResponseEntity<List<Map<String, Object>>> getAcomptes(
            @RequestParam Integer annee
    ) {
        try {
            return ResponseEntity.ok(isService.calculerAcomptes(annee));
        } catch (Exception e) {
            log.error("Erreur lors du calcul des acomptes IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/acomptes/{id}/payer")
    public ResponseEntity<AcompteIS> payerAcompte(@PathVariable String id, @RequestBody(required = false) PaiementAcompteRequest request) {
        try {
            LocalDate datePaiement = request != null ? request.datePaiement() : null;
            Double montantPaye = request != null ? request.montantPaye() : null;
            return ResponseEntity.ok(isService.marquerAcomptePaye(id, datePaiement, montantPaye));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors du paiement d'acompte IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/bareme")
    public ResponseEntity<ISBaremeConfig> getBareme() {
        try {
            return ResponseEntity.ok(isService.getBareme());
        } catch (Exception e) {
            log.error("Erreur lors du chargement du bareme IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/bareme")
    public ResponseEntity<ISBaremeConfig> updateBareme(@RequestBody ISBaremeConfig payload) {
        try {
            return ResponseEntity.ok(isService.updateBareme(payload));
        } catch (Exception e) {
            log.error("Erreur lors de la mise a jour du bareme IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDeclaration(@RequestParam Integer annee) {
        try {
            byte[] file = isService.exportDeclaration(annee);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "declaration_is_" + annee + ".xlsx");
            return ResponseEntity.ok().headers(headers).body(file);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de l'export IS", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

