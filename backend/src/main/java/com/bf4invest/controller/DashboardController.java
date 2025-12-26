package com.bf4invest.controller;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.pdf.PdfService;
import com.bf4invest.service.DashboardService;
import com.bf4invest.service.SoldeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    
    private final DashboardService dashboardService;
    private final PdfService pdfService;
    private final SoldeService soldeService;
    
    @GetMapping("/kpis")
    public ResponseEntity<DashboardKpiResponse> getKPIs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        DashboardKpiResponse kpis = dashboardService.getKPIs(from, to);
        return ResponseEntity.ok(kpis);
    }
    
    @GetMapping("/report/pdf")
    public ResponseEntity<byte[]> generateDashboardReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        HttpHeaders headers = new HttpHeaders();
        try {
            log.info("Génération du rapport PDF - période: {} à {}", from, to);
            
            // Récupérer les KPIs
            DashboardKpiResponse kpis = dashboardService.getKPIs(from, to);
            log.info("KPIs récupérés avec succès");
            
            // Récupérer le solde global actuel
            Double soldeActuel = soldeService.getSoldeGlobalActuel();
            log.info("Solde global: {}", soldeActuel);
            
            // Générer le PDF
            log.info("Début de la génération du PDF...");
            byte[] pdfBytes = pdfService.generateDashboardReport(kpis, from, to, soldeActuel);
            log.info("PDF généré avec succès - taille: {} bytes", pdfBytes.length);
            
            // Préparer les headers pour le téléchargement
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            String filename = "Rapport_Activite_";
            if (from != null && to != null) {
                filename += from + "_" + to;
            } else {
                filename += LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_DATE);
            }
            filename += ".pdf";
            
            headers.setContentDispositionFormData("attachment", filename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport PDF", e);
            e.printStackTrace();
            
            // Retourner une réponse avec headers même en cas d'erreur
            // Les headers CORS seront gérés par CorsConfig
            return ResponseEntity.status(500)
                    .headers(headers)
                    .body(null);
        }
    }
}




