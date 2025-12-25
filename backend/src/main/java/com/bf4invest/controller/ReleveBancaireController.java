package com.bf4invest.controller;

import com.bf4invest.dto.ImportResult;
import com.bf4invest.excel.ReleveBancaireImportService;
import com.bf4invest.model.ReleveBancaireFichier;
import com.bf4invest.model.TransactionBancaire;
import com.bf4invest.service.FileStorageService;
import com.bf4invest.service.TransactionMappingService;
import com.bf4invest.repository.ReleveBancaireFichierRepository;
import com.bf4invest.repository.TransactionBancaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/releve-bancaire")
@RequiredArgsConstructor
public class ReleveBancaireController {
    
    private final ReleveBancaireImportService importService;
    private final TransactionMappingService mappingService;
    private final TransactionBancaireRepository transactionRepository;
    private final FileStorageService fileStorageService;
    private final ReleveBancaireFichierRepository releveFichierRepository;
    
    /**
     * Importe un fichier Excel de relevé bancaire
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importReleve(
            @RequestParam("file") MultipartFile file,
            @RequestParam Integer mois,
            @RequestParam Integer annee
    ) {
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
        
        try {
            ImportResult result = importService.importReleveBancaire(file, mois, annee);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur lors de l'import du relevé bancaire", e);
            ImportResult result = new ImportResult();
            result.getErrors().add("Erreur lors de l'import: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * Mappe automatiquement les transactions non mappées
     */
    @PostMapping("/mapper/{mois}/{annee}")
    public ResponseEntity<Map<String, Integer>> mapperTransactions(
            @PathVariable Integer mois,
            @PathVariable Integer annee
    ) {
        try {
            Map<String, Integer> stats = mappingService.mapperTransactions(mois, annee);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Erreur lors du mapping des transactions", e);
            Map<String, Integer> error = new HashMap<>();
            error.put("error", 1);
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Liste les transactions bancaires
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionBancaire>> getTransactions(
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Boolean mapped
    ) {
        try {
            List<TransactionBancaire> transactions;
            
            if (mois != null && annee != null) {
                if (mapped != null) {
                    transactions = transactionRepository.findByMoisAndAnneeAndMapped(mois, annee, mapped);
                } else {
                    transactions = transactionRepository.findByMoisAndAnnee(mois, annee);
                }
            } else if (mapped != null && mapped == false) {
                transactions = transactionRepository.findByMappedFalse();
            } else {
                transactions = transactionRepository.findAll();
            }
            
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des transactions", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Récupère les détails d'une transaction
     */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionBancaire> getTransaction(@PathVariable String id) {
        Optional<TransactionBancaire> transaction = transactionRepository.findById(id);
        return transaction.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Lie manuellement une transaction à une facture
     */
    @PutMapping("/transactions/{id}/link")
    public ResponseEntity<Void> linkTransaction(
            @PathVariable String id,
            @RequestBody Map<String, String> linkRequest
    ) {
        try {
            String factureVenteId = linkRequest.get("factureVenteId");
            String factureAchatId = linkRequest.get("factureAchatId");
            
            boolean success = mappingService.lierTransactionManuellement(id, factureVenteId, factureAchatId);
            
            if (success) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            log.error("Erreur lors de la liaison de la transaction", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Upload un fichier PDF de relevé bancaire
     */
    @PostMapping("/upload-pdf")
    public ResponseEntity<Map<String, Object>> uploadPdfReleve(
            @RequestParam("file") MultipartFile file,
            @RequestParam Integer mois,
            @RequestParam Integer annee
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le fichier ne peut pas être vide"));
        }
        
        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Seuls les fichiers PDF sont acceptés"));
        }
        
        try {
            // Upload le fichier dans GridFS
            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", "releve_bancaire");
            metadata.put("mois", String.valueOf(mois));
            metadata.put("annee", String.valueOf(annee));
            
            String fileId = fileStorageService.uploadFile(file, metadata);
            
            // Créer les métadonnées dans la collection
            ReleveBancaireFichier releveFichier = ReleveBancaireFichier.builder()
                    .fichierId(fileId)
                    .nomFichier(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .taille(file.getSize())
                    .mois(mois)
                    .annee(annee)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            
            releveFichierRepository.save(releveFichier);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", releveFichier.getId());
            response.put("fileId", fileId);
            response.put("filename", file.getOriginalFilename());
            response.put("mois", mois);
            response.put("annee", annee);
            response.put("message", "Fichier PDF uploadé avec succès");
            
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'upload du PDF", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'upload: " + e.getMessage()));
        }
    }
    
    /**
     * Liste les fichiers PDF de relevés bancaires
     */
    @GetMapping("/pdf-files")
    public ResponseEntity<List<ReleveBancaireFichier>> getPdfFiles(
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee
    ) {
        try {
            List<ReleveBancaireFichier> files;
            
            if (mois != null && annee != null) {
                files = releveFichierRepository.findByMoisAndAnnee(mois, annee);
            } else if (annee != null) {
                files = releveFichierRepository.findByAnnee(annee);
            } else {
                files = releveFichierRepository.findAll();
            }
            
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des fichiers PDF", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Supprime un fichier PDF de relevé bancaire
     */
    @DeleteMapping("/pdf-files/{id}")
    public ResponseEntity<Map<String, String>> deletePdfFile(@PathVariable String id) {
        try {
            Optional<ReleveBancaireFichier> fileOpt = releveFichierRepository.findById(id);
            if (fileOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ReleveBancaireFichier file = fileOpt.get();
            
            // Supprimer le fichier de GridFS
            fileStorageService.deleteFile(file.getFichierId());
            
            // Supprimer les métadonnées
            releveFichierRepository.deleteById(id);
            
            return ResponseEntity.ok(Map.of("message", "Fichier supprimé avec succès"));
        } catch (Exception e) {
            log.error("Erreur lors de la suppression du fichier PDF", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de la suppression: " + e.getMessage()));
        }
    }
}

