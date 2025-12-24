package com.bf4invest.controller;

import com.bf4invest.service.ISService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
}

