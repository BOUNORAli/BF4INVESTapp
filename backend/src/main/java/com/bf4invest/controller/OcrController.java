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

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final CloudinaryOcrService ocrService;

    @PostMapping("/extract-bc")
    public ResponseEntity<OcrExtractResult> extractFromImage(
            @RequestParam("file") MultipartFile file) {
        
        log.info("📄 [OCR] Requête d'extraction OCR - Fichier: {}, Taille: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            log.warn("⚠️ [OCR] Fichier vide");
            return ResponseEntity.badRequest().build();
        }

        // Valider le type de fichier (images uniquement pour OCR)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("⚠️ [OCR] Type de fichier non supporté: {}", contentType);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        try {
            OcrExtractResult result = ocrService.uploadAndExtract(file);
            log.info("✅ [OCR] Extraction réussie - {} lignes détectées", 
                    result.getLignes() != null ? result.getLignes().size() : 0);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("❌ [OCR] Erreur lors de l'extraction OCR", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("❌ [OCR] Erreur inattendue lors de l'extraction OCR", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

