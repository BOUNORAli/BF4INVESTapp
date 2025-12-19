package com.bf4invest.controller;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.pdf.PdfService;
import com.bf4invest.service.DashboardService;
import com.bf4invest.service.SoldeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
        try {
            // Récupérer les KPIs
            DashboardKpiResponse kpis = dashboardService.getKPIs(from, to);
            
            // Récupérer le solde global actuel
            Double soldeActuel = soldeService.getSoldeGlobalActuel();
            
            // Générer le PDF
            byte[] pdfBytes = pdfService.generateDashboardReport(kpis, from, to, soldeActuel);
            
            // Préparer les headers pour le téléchargement
            HttpHeaders headers = new HttpHeaders();
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
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}




