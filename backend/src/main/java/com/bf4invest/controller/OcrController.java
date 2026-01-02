package com.bf4invest.controller;

import com.bf4invest.dto.OcrExtractResult;
import com.bf4invest.service.CloudinaryOcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final CloudinaryOcrService ocrService;

    @PostMapping("/extract-bc")
    public ResponseEntity<?> extractFromImage(
            @RequestParam("file") MultipartFile file) {
        
        log.info("📄 [OCR] Requête d'extraction OCR - Fichier: {}, Taille: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            log.warn("⚠️ [OCR] Fichier vide");
            return ResponseEntity.badRequest().body(Map.of("error", "Le fichier est vide"));
        }

        // Valider le type de fichier (images uniquement pour OCR)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("⚠️ [OCR] Type de fichier non supporté: {}", contentType);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Type de fichier non supporté. Seules les images sont acceptées."));
        }

        try {
            OcrExtractResult result = ocrService.uploadAndExtract(file);
            log.info("✅ [OCR] Extraction réussie - {} lignes détectées", 
                    result.getLignes() != null ? result.getLignes().size() : 0);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.error("❌ [OCR] Erreur de configuration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Configuration OCR manquante: " + e.getMessage()));
        } catch (IOException e) {
            log.error("❌ [OCR] Erreur lors de l'extraction OCR", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'extraction OCR: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ [OCR] Erreur inattendue lors de l'extraction OCR", e);
            log.error("❌ [OCR] Type d'exception: {}, Message: {}", e.getClass().getName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("❌ [OCR] Cause: {}", e.getCause().getMessage());
            }
            // Stack trace pour débogage
            log.error("❌ [OCR] Stack trace complet:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur inattendue: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }
}

