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
 * Si la clé du provider principal est absente mais l'autre est présente, utilise automatiquement celui qui est configuré.
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
        boolean orOk = openRouterOcrService.isConfigured();
        boolean gOk = geminiOcrService.isConfigured();

        if (!orOk && !gOk) {
            throw new IOException(
                    "Aucune clé OCR configurée sur le serveur. Définissez OPENROUTER_API_KEY et/ou GEMINI_API_KEY (ex. variables Railway).");
        }

        String primaryNorm = StringUtils.trimToEmpty(primary).toLowerCase();
        if (primaryNorm.isEmpty()) {
            primaryNorm = "openrouter";
        }

        String effectivePrimary = resolveEffectivePrimary(primaryNorm, orOk, gOk);

        IOException primaryFailure = null;

        if ("gemini".equals(effectivePrimary)) {
            if (gOk) {
                try {
                    log.info("📄 [OCR] Provider: gemini");
                    OcrExtractResult r = geminiOcrService.uploadAndExtract(file);
                    log.info("✅ [OCR] Succès via gemini");
                    return r;
                } catch (IOException e) {
                    primaryFailure = e;
                    log.warn("⚠️ [OCR] Échec gemini: {}", e.getMessage());
                }
            } else {
                primaryFailure = new IOException("Gemini non configuré (GEMINI_API_KEY manquant)");
                log.warn("⚠️ [OCR] {}", primaryFailure.getMessage());
            }
        } else {
            if (orOk) {
                try {
                    log.info("📄 [OCR] Provider: openrouter");
                    OcrExtractResult r = openRouterOcrService.uploadAndExtract(file);
                    log.info("✅ [OCR] Succès via openrouter");
                    return r;
                } catch (IOException e) {
                    primaryFailure = e;
                    log.warn("⚠️ [OCR] Échec openrouter: {}", e.getMessage());
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
            throw new IOException("Aucun provider OCR utilisable pour: " + effectivePrimary);
        }

        if ("gemini".equals(effectivePrimary)) {
            if (orOk) {
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
            if (gOk) {
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
                new IOException("Aucun provider de secours disponible. Vérifiez OPENROUTER_API_KEY, OPENROUTER_MODEL et GEMINI_API_KEY."));
    }

    /**
     * Si le provider demandé n'a pas de clé mais l'autre oui, bascule pour éviter un 500 inutile en prod (ex. Railway sans OpenRouter).
     */
    private String resolveEffectivePrimary(String primaryNorm, boolean orOk, boolean gOk) {
        if ("openrouter".equals(primaryNorm) && !orOk && gOk) {
            log.info("📄 [OCR] OPENROUTER_API_KEY absent — utilisation de Gemini (GEMINI_API_KEY présent)");
            return "gemini";
        }
        if ("gemini".equals(primaryNorm) && !gOk && orOk) {
            log.info("📄 [OCR] GEMINI_API_KEY absent — utilisation d'OpenRouter (OPENROUTER_API_KEY présent)");
            return "openrouter";
        }
        return primaryNorm;
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
