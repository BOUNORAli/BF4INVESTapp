package com.bf4invest.service;

import com.bf4invest.dto.OcrExtractResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt partagé et parsing du JSON métier OCR (identique pour Gemini et OpenRouter).
 */
@Component
@Slf4j
public class OcrJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildBcInvoicePrompt() {
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
     * Parse le texte renvoyé par le modèle (JSON pur ou entouré de fences markdown).
     */
    public OcrExtractResult parseFromAssistantText(String extractedText) throws IOException {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IOException("Réponse modèle vide: aucun contenu texte à parser");
        }

        String jsonText = extractedText.trim();
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();

        JsonNode resultNode = objectMapper.readTree(jsonText);

        OcrExtractResult.OcrExtractResultBuilder builder = OcrExtractResult.builder();

        if (resultNode.has("rawText")) {
            builder.rawText(resultNode.get("rawText").asText());
        }
        if (resultNode.has("numeroDocument")) {
            builder.numeroDocument(resultNode.get("numeroDocument").asText());
        }
        if (resultNode.has("dateDocument")) {
            builder.dateDocument(resultNode.get("dateDocument").asText());
        }
        if (resultNode.has("fournisseurNom")) {
            builder.fournisseurNom(resultNode.get("fournisseurNom").asText());
        }
        if (resultNode.has("confidence")) {
            builder.confidence(resultNode.get("confidence").asDouble(0.0));
        } else {
            builder.confidence(1.0);
        }

        List<OcrExtractResult.OcrProductLine> lignes = new ArrayList<>();
        if (resultNode.has("lignes") && resultNode.get("lignes").isArray()) {
            for (JsonNode ligneNode : resultNode.get("lignes")) {
                OcrExtractResult.OcrProductLine productLine = parseProductLine(ligneNode);
                if (productLine != null) {
                    lignes.add(productLine);
                }
            }
        }
        builder.lignes(lignes);

        OcrExtractResult result = builder.build();
        log.info("✅ [OCR JSON] Parsing réussi - {} lignes, Fournisseur: {}, Date: {}, N°Doc: {}",
                lignes.size(),
                result.getFournisseurNom(),
                result.getDateDocument(),
                result.getNumeroDocument());
        return result;
    }

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
                builder.unite("U");
            }

            OcrExtractResult.OcrProductLine productLine = builder.build();

            if (productLine.getDesignation() == null || productLine.getDesignation().trim().isEmpty()) {
                log.warn("⚠️ [OCR JSON] Ligne ignorée - désignation manquante");
                return null;
            }
            if (productLine.getQuantite() == null || productLine.getQuantite() <= 0) {
                log.warn("⚠️ [OCR JSON] Ligne ignorée - quantité invalide: {}", productLine.getQuantite());
                return null;
            }
            return productLine;
        } catch (Exception e) {
            log.warn("⚠️ [OCR JSON] Erreur parsing ligne: {}", e.getMessage());
            return null;
        }
    }
}
