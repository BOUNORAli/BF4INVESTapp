package com.bf4invest.controller;

import com.bf4invest.excel.ExcelExportService;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.service.BandeCommandeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bandes-commandes")
@RequiredArgsConstructor
public class BandeCommandeController {
    
    private final BandeCommandeService bcService;
    private final ExcelExportService excelExportService;
    
    @GetMapping
    public ResponseEntity<List<BandeCommande>> getAllBCs(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String fournisseurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateMin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateMax,
            @RequestParam(required = false) String etat
    ) {
        List<BandeCommande> bcs = bcService.findAll();
        
        // Filtrer si nécessaire
        if (clientId != null) {
            bcs = bcs.stream().filter(bc -> bc.getClientId().equals(clientId)).toList();
        }
        if (fournisseurId != null) {
            bcs = bcs.stream().filter(bc -> bc.getFournisseurId().equals(fournisseurId)).toList();
        }
        if (etat != null) {
            bcs = bcs.stream().filter(bc -> bc.getEtat().equals(etat)).toList();
        }
        if (dateMin != null) {
            bcs = bcs.stream().filter(bc -> bc.getDateBC() != null && !bc.getDateBC().isBefore(dateMin)).toList();
        }
        if (dateMax != null) {
            bcs = bcs.stream().filter(bc -> bc.getDateBC() != null && !bc.getDateBC().isAfter(dateMax)).toList();
        }
        
        return ResponseEntity.ok(bcs);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<BandeCommande> getBC(@PathVariable String id) {
        return bcService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/check-number")
    public ResponseEntity<Map<String, Boolean>> checkBCNumberAvailability(
            @RequestParam String numeroBC,
            @RequestParam(required = false) String excludeId
    ) {
        boolean available = bcService.isNumeroBCAvailable(numeroBC, excludeId);
        return ResponseEntity.ok(Map.of("available", available));
    }
    
    @PostMapping
    public ResponseEntity<?> createBC(@RequestBody BandeCommande bc) {
        try {
            BandeCommande created = bcService.create(bc);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de la création de la bande de commande: " + e.getMessage()));
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBC(@PathVariable String id, @RequestBody BandeCommande bc) {
        try {
            BandeCommande updated = bcService.update(id, bc);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de la mise à jour de la bande de commande: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBC(@PathVariable String id) {
        bcService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/send-to-supplier")
    public ResponseEntity<Map<String, String>> sendToSupplier(@PathVariable String id) {
        // PDF generation is handled by PdfController
        // Email sending will be implemented in email service
        return ResponseEntity.ok(Map.of("message", "BC ready to send", "bcId", id, "pdfUrl", "/pdf/bandes-commandes/" + id));
    }
    
    @GetMapping("/export/excel")
    public ResponseEntity<ByteArrayResource> exportBCsToExcel(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String fournisseurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateMin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateMax,
            @RequestParam(required = false) String etat
    ) {
        try {
            // Récupérer toutes les BCs
            List<BandeCommande> bcs = bcService.findAll();
            
            // Appliquer les mêmes filtres que getAllBCs
            if (clientId != null) {
                bcs = bcs.stream().filter(bc -> bc.getClientId() != null && bc.getClientId().equals(clientId)).toList();
            }
            if (fournisseurId != null) {
                bcs = bcs.stream().filter(bc -> bc.getFournisseurId() != null && bc.getFournisseurId().equals(fournisseurId)).toList();
            }
            if (etat != null) {
                bcs = bcs.stream().filter(bc -> bc.getEtat() != null && bc.getEtat().equals(etat)).toList();
            }
            if (dateMin != null) {
                bcs = bcs.stream().filter(bc -> bc.getDateBC() != null && !bc.getDateBC().isBefore(dateMin)).toList();
            }
            if (dateMax != null) {
                bcs = bcs.stream().filter(bc -> bc.getDateBC() != null && !bc.getDateBC().isAfter(dateMax)).toList();
            }
            
            // Générer le fichier Excel
            byte[] excelBytes = excelExportService.exportBCsToExcel(bcs);
            
            // Créer le nom de fichier avec la date actuelle
            String fileName = "Export_BCs_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
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
    
    @GetMapping("/export/import-format")
    public ResponseEntity<ByteArrayResource> exportBCsToImportFormat(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String fournisseurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateMin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateMax,
            @RequestParam(required = false) String etat
    ) {
        try {
            // Récupérer toutes les BCs
            List<BandeCommande> bcs = bcService.findAll();
            
            // Appliquer les mêmes filtres que getAllBCs
            if (clientId != null) {
                bcs = bcs.stream().filter(bc -> bc.getClientId() != null && bc.getClientId().equals(clientId)).toList();
            }
            if (fournisseurId != null) {
                bcs = bcs.stream().filter(bc -> bc.getFournisseurId() != null && bc.getFournisseurId().equals(fournisseurId)).toList();
            }
            if (etat != null) {
                bcs = bcs.stream().filter(bc -> bc.getEtat() != null && bc.getEtat().equals(etat)).toList();
            }
            if (dateMin != null) {
                bcs = bcs.stream().filter(bc -> bc.getDateBC() != null && !bc.getDateBC().isBefore(dateMin)).toList();
            }
            if (dateMax != null) {
                bcs = bcs.stream().filter(bc -> bc.getDateBC() != null && !bc.getDateBC().isAfter(dateMax)).toList();
            }
            
            // Générer le fichier Excel au format import
            byte[] excelBytes = excelExportService.exportBCsToImportFormat(bcs);
            
            // Créer le nom de fichier avec la date actuelle
            String fileName = "Export_Import_Format_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
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
}

