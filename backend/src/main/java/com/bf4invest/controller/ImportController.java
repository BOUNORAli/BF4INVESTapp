package com.bf4invest.controller;

import com.bf4invest.dto.ImportResult;
import com.bf4invest.excel.ExcelImportService;
import com.bf4invest.model.ImportLog;
import com.bf4invest.repository.ImportLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {
    
    private final ExcelImportService excelImportService;
    private final ImportLogRepository importLogRepository;
    
    @PostMapping("/excel")
    public ResponseEntity<ImportResult> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            ImportResult result = new ImportResult();
            result.getErrors().add("Fichier vide");
            return ResponseEntity.badRequest().body(result);
        }
        
        if (!file.getOriginalFilename().endsWith(".xlsx") && !file.getOriginalFilename().endsWith(".xls")) {
            ImportResult result = new ImportResult();
            result.getErrors().add("Format de fichier non supporté. Utilisez .xlsx ou .xls");
            return ResponseEntity.badRequest().body(result);
        }
        
        ImportResult result = excelImportService.importExcel(file);
        
        // Store import log
        ImportLog log = ImportLog.builder()
                .fileName(file.getOriginalFilename())
                .details(String.format("%d ligne(s) importée(s) avec succès, %d erreur(s)", 
                        result.getSuccessCount(), result.getErrorCount()))
                .success(result.getErrorCount() == 0 && result.getSuccessCount() > 0)
                .successCount(result.getSuccessCount())
                .errorCount(result.getErrorCount())
                .createdAt(LocalDateTime.now())
                .build();
        importLogRepository.save(log);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> downloadTemplate() {
        try {
            byte[] excelBytes = excelImportService.generateTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Modele_Import_BF4Invest.xlsx");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(excelBytes.length)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new ByteArrayResource(excelBytes));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/history")
    public ResponseEntity<List<ImportLog>> getImportHistory() {
        List<ImportLog> logs = importLogRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/template/produits")
    public ResponseEntity<ByteArrayResource> downloadProductCatalogTemplate() {
        try {
            byte[] excelBytes = excelImportService.generateProductCatalogTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Modele_Catalogue_Produits.xlsx");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(excelBytes.length)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new ByteArrayResource(excelBytes));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/produits")
    public ResponseEntity<?> importProductCatalog(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            ImportResult result = new ImportResult();
            result.getErrors().add("Fichier vide");
            return ResponseEntity.badRequest().body(result);
        }
        
        if (!file.getOriginalFilename().endsWith(".xlsx") && !file.getOriginalFilename().endsWith(".xls")) {
            ImportResult result = new ImportResult();
            result.getErrors().add("Format de fichier non supporté. Utilisez .xlsx ou .xls");
            return ResponseEntity.badRequest().body(result);
        }
        
        ImportResult result = excelImportService.importProductCatalog(file);
        
        // Store import log
        ImportLog log = ImportLog.builder()
                .fileName(file.getOriginalFilename())
                .details(String.format("%d produit(s) importé(s) avec succès, %d erreur(s)", 
                        result.getSuccessCount(), result.getErrorCount()))
                .success(result.getErrorCount() == 0 && result.getSuccessCount() > 0)
                .successCount(result.getSuccessCount())
                .errorCount(result.getErrorCount())
                .createdAt(LocalDateTime.now())
                .build();
        importLogRepository.save(log);
        
        // Générer le fichier de rapport si il y a des erreurs ou des succès
        if (!result.getErrorRows().isEmpty() || !result.getSuccessRows().isEmpty()) {
            try {
                byte[] reportBytes = excelImportService.generateImportReport(result, file);
                
                HttpHeaders headers = new HttpHeaders();
                String reportFileName = file.getOriginalFilename() != null 
                        ? file.getOriginalFilename().replace(".xlsx", "_rapport.xlsx").replace(".xls", "_rapport.xlsx")
                        : "rapport_import.xlsx";
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + reportFileName);
                headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                
                return ResponseEntity.ok()
                        .headers(headers)
                        .contentLength(reportBytes.length)
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(new ByteArrayResource(reportBytes));
            } catch (Exception e) {
                // Si erreur lors de la génération du rapport, retourner juste le résultat
                return ResponseEntity.ok(result);
            }
        }
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/template/operations")
    public ResponseEntity<ByteArrayResource> downloadOperationsComptablesTemplate() {
        try {
            byte[] excelBytes = excelImportService.generateOperationsComptablesTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Modele_Operations_Comptables.xlsx");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(excelBytes.length)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new ByteArrayResource(excelBytes));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/operations")
    public ResponseEntity<?> importOperationsComptables(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            ImportResult result = new ImportResult();
            result.getErrors().add("Fichier vide");
            return ResponseEntity.badRequest().body(result);
        }
        
        if (!file.getOriginalFilename().endsWith(".xlsx") && !file.getOriginalFilename().endsWith(".xls")) {
            ImportResult result = new ImportResult();
            result.getErrors().add("Format de fichier non supporté. Utilisez .xlsx ou .xls");
            return ResponseEntity.badRequest().body(result);
        }
        
        ImportResult result = excelImportService.importOperationsComptables(file);
        
        // Store import log
        ImportLog log = ImportLog.builder()
                .fileName(file.getOriginalFilename())
                .details(String.format("%d opération(s) importée(s) avec succès, %d erreur(s)", 
                        result.getSuccessCount(), result.getErrorCount()))
                .success(result.getErrorCount() == 0 && result.getSuccessCount() > 0)
                .successCount(result.getSuccessCount())
                .errorCount(result.getErrorCount())
                .createdAt(LocalDateTime.now())
                .build();
        importLogRepository.save(log);
        
        // Générer le fichier de rapport si il y a des erreurs ou des succès
        if (!result.getErrorRows().isEmpty() || !result.getSuccessRows().isEmpty()) {
            try {
                byte[] reportBytes = excelImportService.generateImportReport(result, file);
                
                HttpHeaders headers = new HttpHeaders();
                String reportFileName = file.getOriginalFilename() != null 
                        ? file.getOriginalFilename().replace(".xlsx", "_rapport.xlsx").replace(".xls", "_rapport.xlsx")
                        : "rapport_import.xlsx";
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + reportFileName);
                headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                
                return ResponseEntity.ok()
                        .headers(headers)
                        .contentLength(reportBytes.length)
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(new ByteArrayResource(reportBytes));
            } catch (Exception e) {
                // Si erreur lors de la génération du rapport, retourner juste le résultat
                return ResponseEntity.ok(result);
            }
        }
        
        return ResponseEntity.ok(result);
    }
}


