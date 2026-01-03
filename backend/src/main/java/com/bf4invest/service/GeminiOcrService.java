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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class GeminiOcrService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.api-url:https://generativelanguage.googleapis.com/v1}")
    private String apiUrl;

    @Value("${gemini.model:gemini-1.5-flash-latest}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiOcrService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extrait les informations d'une facture depuis une image en utilisant Gemini Pro Vision
     */
    public OcrExtractResult uploadAndExtract(MultipartFile file) throws IOException {
        log.info("🔍 [Gemini OCR] Début extraction - Fichier: {}, Taille: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        // Vérifier la configuration
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("Configuration Gemini manquante: api-key est requis");
        }

        // Convertir l'image en base64
        String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg"; // Par défaut
        }

        log.debug("📤 [Gemini OCR] Image convertie en base64 - Taille: {} caractères, MIME: {}", 
                base64Image.length(), mimeType);

        // Construire le prompt
        String prompt = buildPrompt();

        // Appeler l'API Gemini
        String jsonResponse = callGeminiAPI(base64Image, mimeType, prompt);

        // Parser la réponse
        OcrExtractResult result = parseGeminiResponse(jsonResponse);

        log.info("✅ [Gemini OCR] Extraction réussie - {} lignes détectées", 
                result.getLignes() != null ? result.getLignes().size() : 0);

        return result;
    }

    /**
     * Construit le prompt système pour Gemini
     */
    private String buildPrompt() {
        return """
                Tu es un expert en extraction de données de factures et bons de commande marocains.
                
                Analyse cette image de facture et extrais les informations suivantes au format JSON strict.
                
                IMPORTANT: Tu DOIS retourner UNIQUEMENT un objet JSON valide, sans aucun texte avant ou après, sans markdown, sans code blocks.
                
                Format JSON requis:
                {
                  "rawText": "Le texte brut complet extrait de la facture",
                  "numeroDocument": "Le numéro de la facture ou bon de commande (ex: F01054/25, 000002366)",
                  "dateDocument": "La date de la facture au format ISO YYYY-MM-DD (ex: 2025-05-10)",
                  "fournisseurNom": "Le nom complet de l'entreprise fournisseur émettrice (ex: SORIMAC S.A.R.L, GUARIMETAL sarl)",
                  "lignes": [
                    {
                      "designation": "La désignation complète du produit (ex: FER TOR/500 DIAM 12)",
                      "quantite": 123.0,
                      "prixUnitaireHT": 45.50,
                      "prixTotalHT": 5596.50,
                      "unite": "U"
                    }
                  ],
                  "confidence": 0.95
                }
                
                Instructions importantes:
                - Ignore les informations de bruit (dates de transactions bancaires, numéros de téléphone, adresses, etc.)
                - Extrait SEULEMENT les lignes de produits réels du tableau de la facture
                - La quantité, le prix unitaire et le prix total doivent être des nombres décimaux
                - Le prix unitaire doit être en HT (Hors Taxe)
                - Si le prix est en TTC (Toutes Taxes Comprises), convertis-le en HT en divisant par 1.2 (si TVA 20%)
                - Pour l'unité, utilise "U" par défaut si non spécifié
                - La date doit être au format YYYY-MM-DD
                - Le numéro de document doit être exactement comme affiché sur la facture
                - Ne confonds pas le fournisseur (émetteur) avec le client (destinataire)
                - RÉPONSE REQUISE: Retourne UNIQUEMENT le JSON brut, valide, sans markdown, sans ```json, sans explications
                """;
    }

    /**
     * Appelle l'API Gemini Pro Vision
     */
    private String callGeminiAPI(String base64Image, String mimeType, String prompt) throws IOException {
        try {
            String url = String.format("%s/models/%s:generateContent?key=%s", apiUrl, model, apiKey);
            
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
            // Note: response_mime_type is only available in v1beta API, not in v1
            // We rely on the prompt to ensure JSON response format
            requestBody.put("generationConfig", generationConfig);

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
                            return clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("❌ [Gemini OCR] Détails de l'erreur: {}", errorBody);
                                        try {
                                            // Essayer de parser l'erreur JSON de Gemini
                                            JsonNode errorNode = objectMapper.readTree(errorBody);
                                            String errorMessage = "Erreur API Gemini";
                                            if (errorNode.has("error") && errorNode.get("error").has("message")) {
                                                errorMessage = errorNode.get("error").get("message").asText();
                                            } else if (errorNode.has("error")) {
                                                errorMessage = errorNode.get("error").asText();
                                            }
                                            return Mono.error(new IOException("Erreur API Gemini (" + clientResponse.statusCode() + "): " + errorMessage));
                                        } catch (Exception e) {
                                            return Mono.error(new IOException("Erreur API Gemini (" + clientResponse.statusCode() + "): " + errorBody));
                                        }
                                    });
                        })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorMap(java.util.concurrent.TimeoutException.class, e -> 
                        new IOException("Timeout lors de l'appel à l'API Gemini (60s dépassés)", e))
                    .block();

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

            // Parser le JSON extrait (peut être entouré de markdown ou autres caractères)
            String jsonText = extractedText.trim();
            
            // Nettoyer le JSON si nécessaire (retirer ```json et ```)
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.substring(7);
            } else if (jsonText.startsWith("```")) {
                jsonText = jsonText.substring(3);
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.substring(0, jsonText.length() - 3);
            }
            jsonText = jsonText.trim();

            // Parser le JSON final
            JsonNode resultNode = objectMapper.readTree(jsonText);

            // Construire OcrExtractResult
            OcrExtractResult.OcrExtractResultBuilder builder = OcrExtractResult.builder();

            // Raw text
            if (resultNode.has("rawText")) {
                builder.rawText(resultNode.get("rawText").asText());
            }

            // Numéro document
            if (resultNode.has("numeroDocument")) {
                builder.numeroDocument(resultNode.get("numeroDocument").asText());
            }

            // Date document
            if (resultNode.has("dateDocument")) {
                builder.dateDocument(resultNode.get("dateDocument").asText());
            }

            // Fournisseur
            if (resultNode.has("fournisseurNom")) {
                builder.fournisseurNom(resultNode.get("fournisseurNom").asText());
            }

            // Confidence
            if (resultNode.has("confidence")) {
                builder.confidence(resultNode.get("confidence").asDouble(0.0));
            } else {
                builder.confidence(1.0); // Par défaut
            }

            // Lignes de produits
            List<OcrExtractResult.OcrProductLine> lignes = new ArrayList<>();
            if (resultNode.has("lignes") && resultNode.get("lignes").isArray()) {
                JsonNode lignesArray = resultNode.get("lignes");
                for (JsonNode ligneNode : lignesArray) {
                    OcrExtractResult.OcrProductLine productLine = parseProductLine(ligneNode);
                    if (productLine != null) {
                        lignes.add(productLine);
                    }
                }
            }
            builder.lignes(lignes);

            OcrExtractResult result = builder.build();

            log.info("✅ [Gemini OCR] Parsing réussi - {} lignes, Fournisseur: {}, Date: {}, N°Doc: {}", 
                    lignes.size(), 
                    result.getFournisseurNom(), 
                    result.getDateDocument(), 
                    result.getNumeroDocument());

            return result;

        } catch (IOException e) {
            log.error("❌ [Gemini OCR] Erreur lors du parsing de la réponse JSON", e);
            throw e;
        } catch (Exception e) {
            log.error("❌ [Gemini OCR] Erreur inattendue lors du parsing", e);
            throw new IOException("Erreur lors du parsing de la réponse Gemini: " + e.getMessage(), e);
        }
    }

    /**
     * Parse une ligne de produit depuis un JsonNode
     */
    private OcrExtractResult.OcrProductLine parseProductLine(JsonNode ligneNode) {
        try {
            OcrExtractResult.OcrProductLine.OcrProductLineBuilder builder = 
                    OcrExtractResult.OcrProductLine.builder();

            if (ligneNode.has("designation")) {
                builder.designation(ligneNode.get("designation").asText());
            }

            if (ligneNode.has("quantite")) {
                builder.quantite(ligneNode.get("quantite").asDouble());
            }

            if (ligneNode.has("prixUnitaireHT")) {
                builder.prixUnitaireHT(ligneNode.get("prixUnitaireHT").asDouble());
            }

            if (ligneNode.has("prixTotalHT")) {
                builder.prixTotalHT(ligneNode.get("prixTotalHT").asDouble());
            }

            if (ligneNode.has("unite")) {
                builder.unite(ligneNode.get("unite").asText());
            } else {
                builder.unite("U"); // Par défaut
            }

            OcrExtractResult.OcrProductLine productLine = builder.build();

            // Validation: doit avoir au moins une désignation et une quantité
            if (productLine.getDesignation() == null || productLine.getDesignation().trim().isEmpty()) {
                log.warn("⚠️ [Gemini OCR] Ligne de produit ignorée - désignation manquante");
                return null;
            }

            if (productLine.getQuantite() == null || productLine.getQuantite() <= 0) {
                log.warn("⚠️ [Gemini OCR] Ligne de produit ignorée - quantité invalide: {}", productLine.getQuantite());
                return null;
            }

            return productLine;

        } catch (Exception e) {
            log.warn("⚠️ [Gemini OCR] Erreur lors du parsing d'une ligne de produit: {}", e.getMessage());
            return null;
        }
    }
}

