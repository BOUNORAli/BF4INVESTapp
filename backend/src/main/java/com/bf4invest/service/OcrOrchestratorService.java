package com.bf4invest.service;

import com.bf4invest.dto.OcrExtractResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Orchestre les providers OCR : OpenRouter (principal par défaut) avec repli Gemini.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OcrOrchestratorService {

    private final OpenRouterOcrService openRouterOcrService;
    private final GeminiOcrService geminiOcrService;

    @Value("${ocr.provider.primary:openrouter}")
    private String primary;

    @Value("${ocr.provider.fallback-enabled:true}")
    private boolean fallbackEnabled;

    public OcrExtractResult uploadAndExtract(MultipartFile file) throws IOException {
        String primaryNorm = StringUtils.trimToEmpty(primary).toLowerCase();
        if (primaryNorm.isEmpty()) {
            primaryNorm = "openrouter";
        }

        IOException primaryFailure = null;

        if ("gemini".equals(primaryNorm)) {
            if (geminiOcrService.isConfigured()) {
                try {
                    log.info("📄 [OCR] Provider principal: gemini");
                    OcrExtractResult r = geminiOcrService.uploadAndExtract(file);
                    log.info("✅ [OCR] Succès via gemini");
                    return r;
                } catch (IOException e) {
                    primaryFailure = e;
                    log.warn("⚠️ [OCR] Échec provider principal gemini: {}", e.getMessage());
                }
            } else {
                primaryFailure = new IOException("Gemini non configuré (GEMINI_API_KEY manquant)");
                log.warn("⚠️ [OCR] {}", primaryFailure.getMessage());
            }
        } else {
            // default: openrouter
            if (openRouterOcrService.isConfigured()) {
                try {
                    log.info("📄 [OCR] Provider principal: openrouter");
                    OcrExtractResult r = openRouterOcrService.uploadAndExtract(file);
                    log.info("✅ [OCR] Succès via openrouter");
                    return r;
                } catch (IOException e) {
                    primaryFailure = e;
                    log.warn("⚠️ [OCR] Échec provider principal openrouter: {}", e.getMessage());
                }
            } else {
                primaryFailure = new IOException("OpenRouter non configuré (OPENROUTER_API_KEY manquant)");
                log.warn("⚠️ [OCR] {}", primaryFailure.getMessage());
            }
        }

        if (!fallbackEnabled) {
            if (primaryFailure != null) {
                throw primaryFailure;
            }
            throw new IOException("Aucun provider OCR configuré pour le mode principal: " + primaryNorm);
        }

        // Fallback
        if ("gemini".equals(primaryNorm)) {
            if (openRouterOcrService.isConfigured()) {
                try {
                    log.info("🔄 [OCR] Fallback: openrouter");
                    OcrExtractResult r = openRouterOcrService.uploadAndExtract(file);
                    log.info("✅ [OCR] Succès via fallback openrouter");
                    return r;
                } catch (IOException e) {
                    log.error("❌ [OCR] Fallback openrouter échoué: {}", e.getMessage());
                    throw combinedFailure(primaryFailure, e);
                }
            }
        } else {
            if (geminiOcrService.isConfigured()) {
                try {
                    log.info("🔄 [OCR] Fallback: gemini");
                    OcrExtractResult r = geminiOcrService.uploadAndExtract(file);
                    log.info("✅ [OCR] Succès via fallback gemini");
                    return r;
                } catch (IOException e) {
                    log.error("❌ [OCR] Fallback gemini échoué: {}", e.getMessage());
                    throw combinedFailure(primaryFailure, e);
                }
            }
        }

        throw combinedFailure(primaryFailure,
                new IOException("Aucun provider de secours disponible (configurez l'autre clé API ou désactivez le mode principal actuel)"));
    }

    private IOException combinedFailure(IOException primary, IOException secondary) {
        String p = primary != null ? primary.getMessage() : "n/a";
        String s = secondary != null ? secondary.getMessage() : "n/a";
        return new IOException("Échec OCR principal et secours. Principal: " + p + " | Secours: " + s, secondary != null ? secondary : primary);
    }

    public OcrDiagnosticStatus getDiagnosticStatus() {
        return new OcrDiagnosticStatus(
                primary,
                fallbackEnabled,
                openRouterOcrService.isConfigured(),
                geminiOcrService.isConfigured(),
                openRouterOcrService.getProviderId(),
                geminiOcrService.getProviderId(),
                openRouterOcrService.getModelName(),
                geminiOcrService.getModelName()
        );
    }

    public String listGeminiModels() throws IOException {
        return geminiOcrService.listAvailableModels();
    }

    public record OcrDiagnosticStatus(
            String configuredPrimary,
            boolean fallbackEnabled,
            boolean openRouterConfigured,
            boolean geminiConfigured,
            String openRouterProviderId,
            String geminiProviderId,
            String openRouterModel,
            String geminiModel
    ) {}
}
