package com.bf4invest.controller;

import com.bf4invest.dto.OcrExtractResult;
import com.bf4invest.service.OcrOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final OcrOrchestratorService ocrOrchestratorService;

    /**
     * Liste des modèles Gemini (diagnostic historique, clé GEMINI_API_KEY requise).
     */
    @GetMapping("/diagnostic/models")
    public ResponseEntity<?> listAvailableModels() {
        try {
            String modelsJson = ocrOrchestratorService.listGeminiModels();
            return ResponseEntity.ok(Map.of("models", modelsJson));
        } catch (Exception e) {
            log.error("❌ [OCR Diagnostic] Erreur lors de la liste des modèles Gemini", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de la liste des modèles: " + e.getMessage()));
        }
    }

    /**
     * État des providers OCR (OpenRouter / Gemini), clés présentes, modèles configurés.
     */
    @GetMapping("/diagnostic/providers")
    public ResponseEntity<OcrOrchestratorService.OcrDiagnosticStatus> diagnosticProviders() {
        return ResponseEntity.ok(ocrOrchestratorService.getDiagnosticStatus());
    }

    @PostMapping("/extract-bc")
    public ResponseEntity<?> extractFromImage(
            @RequestParam("file") MultipartFile file) {

        log.info("📄 [OCR] Requête d'extraction OCR - Fichier: {}, Taille: {} bytes",
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            log.warn("⚠️ [OCR] Fichier vide");
            return ResponseEntity.badRequest().body(Map.of("error", "Le fichier est vide"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("⚠️ [OCR] Type de fichier non supporté: {}", contentType);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Type de fichier non supporté. Seules les images sont acceptées."));
        }

        try {
            OcrExtractResult result = ocrOrchestratorService.uploadAndExtract(file);
            log.info("✅ [OCR] Extraction réussie - {} lignes détectées",
                    result.getLignes() != null ? result.getLignes().size() : 0);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("❌ [OCR] Erreur lors de l'extraction OCR", e);
            HttpStatus status = ocrFailureHttpStatus(e.getMessage());
            return ResponseEntity.status(status)
                    .body(Map.of("error", "Erreur lors de l'extraction OCR: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ [OCR] Erreur inattendue lors de l'extraction OCR", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur inattendue: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }

    /**
     * Quotas / saturation des providers gratuits : 503 plutôt que 500.
     */
    private static HttpStatus ocrFailureHttpStatus(String message) {
        if (message == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("429")
                || m.contains("too_many_requests")
                || m.contains("quota exceeded")
                || m.contains("rate limit")
                || m.contains("resource exhausted")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (m.contains("no endpoints found")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
