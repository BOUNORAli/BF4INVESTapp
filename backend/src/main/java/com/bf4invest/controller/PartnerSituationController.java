package com.bf4invest.controller;

import com.bf4invest.dto.PartnerSituationResponse;
import com.bf4invest.excel.PartnerSituationExcelExporter;
import com.bf4invest.pdf.generator.PartnerSituationPdfGenerator;
import com.bf4invest.service.PartnerSituationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/partner-situation")
@RequiredArgsConstructor
public class PartnerSituationController {
    
    private final PartnerSituationService partnerSituationService;
    private final PartnerSituationPdfGenerator pdfGenerator;
    private final PartnerSituationExcelExporter excelExporter;
    
    @GetMapping("/client/{id}")
    public ResponseEntity<PartnerSituationResponse> getClientSituation(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            PartnerSituationResponse situation = partnerSituationService.getClientSituation(id, from, to);
            return ResponseEntity.ok(situation);
        } catch (RuntimeException e) {
            log.error("Erreur lors de la récupération de la situation client {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la situation client", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/supplier/{id}")
    public ResponseEntity<PartnerSituationResponse> getSupplierSituation(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            PartnerSituationResponse situation = partnerSituationService.getSupplierSituation(id, from, to);
            return ResponseEntity.ok(situation);
        } catch (RuntimeException e) {
            log.error("Erreur lors de la récupération de la situation fournisseur {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la situation fournisseur", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/client/{id}/export/pdf")
    public ResponseEntity<byte[]> exportClientSituationPdf(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            PartnerSituationResponse situation = partnerSituationService.getClientSituation(id, from, to);
            byte[] pdfBytes = pdfGenerator.generate(situation);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                    "situation_" + situation.getPartnerInfo().getNom().replaceAll("[^a-zA-Z0-9]", "_") + "_" + 
                    LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF pour client {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/client/{id}/export/excel")
    public ResponseEntity<byte[]> exportClientSituationExcel(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            PartnerSituationResponse situation = partnerSituationService.getClientSituation(id, from, to);
            byte[] excelBytes = excelExporter.export(situation);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", 
                    "situation_" + situation.getPartnerInfo().getNom().replaceAll("[^a-zA-Z0-9]", "_") + "_" + 
                    LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'Excel pour client {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/supplier/{id}/export/pdf")
    public ResponseEntity<byte[]> exportSupplierSituationPdf(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            PartnerSituationResponse situation = partnerSituationService.getSupplierSituation(id, from, to);
            byte[] pdfBytes = pdfGenerator.generate(situation);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                    "situation_" + situation.getPartnerInfo().getNom().replaceAll("[^a-zA-Z0-9]", "_") + "_" + 
                    LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF pour fournisseur {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/supplier/{id}/export/excel")
    public ResponseEntity<byte[]> exportSupplierSituationExcel(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            PartnerSituationResponse situation = partnerSituationService.getSupplierSituation(id, from, to);
            byte[] excelBytes = excelExporter.export(situation);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", 
                    "situation_" + situation.getPartnerInfo().getNom().replaceAll("[^a-zA-Z0-9]", "_") + "_" + 
                    LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'Excel pour fournisseur {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

