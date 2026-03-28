package com.bf4invest.service;

import com.bf4invest.dto.OcrExtractResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class GeminiOcrService implements DocumentOcrProvider {

    private final OcrJsonParser ocrJsonParser;

    @Value("${gemini.api-key:}")
    private String apiKey;

    // v1beta est recommandé pour le support de response_mime_type (JSON structuré)
    @Value("${gemini.api-url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    // Modèle vérifié disponible avec cette clé API (liste obtenue via /v1beta/models)
    // gemini-2.0-flash-001 : Version stable rapide et versatile (janvier 2025)
    // Alternatives disponibles: gemini-2.0-flash, gemini-2.5-flash, gemini-flash-latest
    // NOTE: gemini-1.5-flash n'existe PAS dans la liste des modèles disponibles
    @Value("${gemini.model:gemini-2.0-flash-001}")
    private String model;

    @Value("${ocr.timeout-seconds.gemini:60}")
    private int timeoutSeconds;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiOcrService(OcrJsonParser ocrJsonParser) {
        this.ocrJsonParser = ocrJsonParser;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderId() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(apiKey);
    }

    /**
     * Liste les modèles disponibles avec cette clé API (méthode de diagnostic)
     */
    public String listAvailableModels() throws IOException {
        try {
            String listUrl = String.format("%s/models?key=%s", apiUrl, apiKey);
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"E\",\"location\":\"GeminiOcrService.java:listAvailableModels\",\"message\":\"Listing available models\",\"data\":{\"apiUrl\":\"%s\",\"listUrl\":\"%s\"},\"timestamp\":%d}%n", 
                    apiUrl, listUrl.replace(apiKey, "***"), System.currentTimeMillis()));
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            log.info("🔍 [Gemini Diagnostic] Liste des modèles disponibles - URL: {}", listUrl.replace(apiKey, "***"));
            
            String response = webClient.get()
                    .uri(listUrl)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> {
                            log.error("❌ [Gemini Diagnostic] Erreur lors de la liste des modèles - Status: {}", clientResponse.statusCode());
                            return clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("❌ [Gemini Diagnostic] Détails de l'erreur: {}", errorBody);
                                        
                                        // #region agent log
                                        try {
                                            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                                            fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"E\",\"location\":\"GeminiOcrService.java:listAvailableModels\",\"message\":\"Error listing models\",\"data\":{\"status\":%d,\"error\":\"%s\"},\"timestamp\":%d}%n", 
                                                clientResponse.statusCode(), errorBody.replace("\"", "\\\"").substring(0, Math.min(200, errorBody.length())), System.currentTimeMillis()));
                                            fw.close();
                                        } catch (Exception e) {}
                                        // #endregion
                                        
                                        return Mono.error(new IOException("Erreur lors de la liste des modèles (" + clientResponse.statusCode() + "): " + errorBody));
                                    });
                        })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\",\"location\":\"GeminiOcrService.java:listAvailableModels\",\"message\":\"Models list received\",\"data\":{\"responseLength\":%d},\"timestamp\":%d}%n", 
                    response != null ? response.length() : 0, System.currentTimeMillis()));
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            log.info("✅ [Gemini Diagnostic] Liste des modèles reçue - Taille: {} caractères", response != null ? response.length() : 0);
            return response;
            
        } catch (Exception e) {
            log.error("❌ [Gemini Diagnostic] Erreur inattendue lors de la liste des modèles", e);
            throw new IOException("Erreur lors de la liste des modèles: " + e.getMessage(), e);
        }
    }

    /**
     * Extrait les informations d'une facture depuis une image en utilisant Gemini Pro Vision
     */
    @Override
    public OcrExtractResult uploadAndExtract(MultipartFile file) throws IOException {
        log.info("🔍 [Gemini OCR] Début extraction - Fichier: {}, Taille: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        // Vérifier la configuration
        if (StringUtils.isBlank(apiKey)) {
            throw new IOException("Configuration Gemini manquante: GEMINI_API_KEY est requis");
        }

        // Convertir l'image en base64
        String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg"; // Par défaut
        }

        log.debug("📤 [Gemini OCR] Image convertie en base64 - Taille: {} caractères, MIME: {}", 
                base64Image.length(), mimeType);

        String prompt = ocrJsonParser.buildBcInvoicePrompt();

        // Appeler l'API Gemini
        String jsonResponse = callGeminiAPI(base64Image, mimeType, prompt);

        // Parser la réponse
        OcrExtractResult result = parseGeminiResponse(jsonResponse);

        log.info("✅ [Gemini OCR] Extraction réussie - {} lignes détectées", 
                result.getLignes() != null ? result.getLignes().size() : 0);

        return result;
    }

    /**
     * Appelle l'API Gemini Pro Vision
     */
    private String callGeminiAPI(String base64Image, String mimeType, String prompt) throws IOException {
        try {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"API call starting\",\"data\":{\"apiUrl\":\"%s\",\"model\":\"%s\",\"mimeType\":\"%s\",\"imageSize\":%d},\"timestamp\":%d}%n", 
                    apiUrl, model, mimeType, base64Image.length(), System.currentTimeMillis()));
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            String url = String.format("%s/models/%s:generateContent?key=%s", apiUrl, model, apiKey);
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"URL constructed\",\"data\":{\"url\":\"%s\"},\"timestamp\":%d}%n", 
                    url.replace(apiKey, "***"), System.currentTimeMillis()));
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            log.debug("📡 [Gemini OCR] Appel API: {}", url.replace(apiKey, "***"));

            // Construire le body de la requête
            Map<String, Object> requestBody = new HashMap<>();
            
            // Contenu avec prompt et image
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            
            // Pour Gemini Vision, on peut mettre l'image ou le texte en premier
            // On met d'abord le texte (instructions), puis l'image
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);
            parts.add(textPart);
            
            // Partie image (base64)
            Map<String, Object> imagePart = new HashMap<>();
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Image);
            imagePart.put("inline_data", inlineData);
            parts.add(imagePart);
            
            content.put("parts", parts);
            
            List<Map<String, Object>> contents = new ArrayList<>();
            contents.add(content);
            requestBody.put("contents", contents);
            
            // Configuration de génération
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.1);
            // response_mime_type est supporté dans v1beta pour forcer le format JSON
            generationConfig.put("response_mime_type", "application/json");
            requestBody.put("generationConfig", generationConfig);

            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"Request body prepared\",\"data\":{\"model\":\"%s\",\"hasResponseMimeType\":true,\"temperature\":0.1},\"timestamp\":%d}%n", 
                    model, System.currentTimeMillis()));
                fw.close();
            } catch (Exception e) {}
            // #endregion

            log.info("📤 [Gemini OCR] Envoi requête à Gemini API...");
            log.debug("📤 [Gemini OCR] URL: {}", url.replace(apiKey, "***"));
            log.debug("📤 [Gemini OCR] Modèle: {}", model);

            // Faire l'appel HTTP avec gestion d'erreurs
            String response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                        clientResponse -> {
                            log.error("❌ [Gemini OCR] Erreur HTTP de l'API Gemini - Status: {}", clientResponse.statusCode());
                            
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"HTTP error received\",\"data\":{\"status\":%d,\"model\":\"%s\"},\"timestamp\":%d}%n", 
                                    clientResponse.statusCode(), model, System.currentTimeMillis()));
                                fw.close();
                            } catch (Exception e) {}
                            // #endregion
                            
                            return clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("❌ [Gemini OCR] Détails de l'erreur: {}", errorBody);
                                        
                                        // #region agent log
                                        try {
                                            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                                            String errorPreview = errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody;
                                            fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"Error body received\",\"data\":{\"status\":%d,\"errorPreview\":\"%s\"},\"timestamp\":%d}%n", 
                                                clientResponse.statusCode(), errorPreview.replace("\"", "\\\"").replace("\n", "\\n"), System.currentTimeMillis()));
                                            fw.close();
                                        } catch (Exception e) {}
                                        // #endregion
                                        
                                        try {
                                            // Essayer de parser l'erreur JSON de Gemini
                                            JsonNode errorNode = objectMapper.readTree(errorBody);
                                            String errorMessage = "Erreur API Gemini";
                                            if (errorNode.has("error") && errorNode.get("error").has("message")) {
                                                errorMessage = errorNode.get("error").get("message").asText();
                                            } else if (errorNode.has("error")) {
                                                errorMessage = errorNode.get("error").asText();
                                            }
                                            
                                            // #region agent log
                                            try {
                                                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                                                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"Parsed error message\",\"data\":{\"errorMessage\":\"%s\"},\"timestamp\":%d}%n", 
                                                    errorMessage.replace("\"", "\\\""), System.currentTimeMillis()));
                                                fw.close();
                                            } catch (Exception e) {}
                                            // #endregion
                                            
                                            return Mono.error(new IOException("Erreur API Gemini (" + clientResponse.statusCode() + "): " + errorMessage));
                                        } catch (Exception e) {
                                            return Mono.error(new IOException("Erreur API Gemini (" + clientResponse.statusCode() + "): " + errorBody));
                                        }
                                    });
                        })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)))
                    .onErrorMap(java.util.concurrent.TimeoutException.class, e ->
                        new IOException("Timeout lors de l'appel à l'API Gemini (" + timeoutSeconds + "s dépassés)", e))
                    .block();

            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\PC\\Documents\\BF4INVESTapp\\.cursor\\debug.log", true);
                fw.write(String.format("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\",\"location\":\"GeminiOcrService.java:callGeminiAPI\",\"message\":\"Response received\",\"data\":{\"responseLength\":%d,\"model\":\"%s\"},\"timestamp\":%d}%n", 
                    response != null ? response.length() : 0, model, System.currentTimeMillis()));
                fw.close();
            } catch (Exception e) {}
            // #endregion

            log.debug("📥 [Gemini OCR] Réponse reçue - Taille: {} caractères", response != null ? response.length() : 0);

            if (response == null || response.trim().isEmpty()) {
                throw new IOException("Réponse vide de l'API Gemini");
            }

            return response;

        } catch (IOException e) {
            // Réémettre les IOException telles quelles
            throw e;
        } catch (Exception e) {
            log.error("❌ [Gemini OCR] Erreur inattendue lors de l'appel à l'API Gemini", e);
            throw new IOException("Erreur lors de l'appel à l'API Gemini: " + e.getMessage(), e);
        }
    }

    /**
     * Parse la réponse JSON de Gemini vers OcrExtractResult
     */
    private OcrExtractResult parseGeminiResponse(String jsonResponse) throws IOException {
        try {
            log.debug("🔍 [Gemini OCR] Parsing de la réponse JSON...");

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // Extraire le texte de la réponse (dans candidates[0].content.parts[0].text)
            String extractedText = null;
            if (rootNode.has("candidates") && rootNode.get("candidates").isArray() && 
                rootNode.get("candidates").size() > 0) {
                JsonNode candidate = rootNode.get("candidates").get(0);
                if (candidate.has("content") && candidate.get("content").has("parts")) {
                    JsonNode parts = candidate.get("content").get("parts");
                    if (parts.isArray() && parts.size() > 0 && parts.get(0).has("text")) {
                        extractedText = parts.get(0).get("text").asText();
                    }
                }
            }

            if (extractedText == null || extractedText.trim().isEmpty()) {
                // Vérifier s'il y a une erreur dans la réponse
                if (rootNode.has("error")) {
                    String errorMessage = "Erreur inconnue";
                    if (rootNode.get("error").has("message")) {
                        errorMessage = rootNode.get("error").get("message").asText();
                    } else {
                        errorMessage = rootNode.get("error").toString();
                    }
                    log.error("❌ [Gemini OCR] Erreur dans la réponse Gemini: {}", errorMessage);
                    throw new IOException("Erreur API Gemini: " + errorMessage);
                }
                log.warn("⚠️ [Gemini OCR] Aucun texte extrait dans la réponse");
                throw new IOException("Réponse Gemini invalide: aucun contenu texte trouvé. Réponse complète: " + jsonResponse.substring(0, Math.min(500, jsonResponse.length())));
            }

            log.debug("📄 [Gemini OCR] Texte extrait - Taille: {} caractères", extractedText.length());
            log.debug("📄 [Gemini OCR] Contenu: {}", extractedText.substring(0, Math.min(200, extractedText.length())));

            return ocrJsonParser.parseFromAssistantText(extractedText);

        } catch (IOException e) {
            log.error("❌ [Gemini OCR] Erreur lors du parsing de la réponse JSON", e);
            throw e;
        } catch (Exception e) {
            log.error("❌ [Gemini OCR] Erreur inattendue lors du parsing", e);
            throw new IOException("Erreur lors du parsing de la réponse Gemini: " + e.getMessage(), e);
        }
    }

    public String getModelName() {
        return model;
    }
}

