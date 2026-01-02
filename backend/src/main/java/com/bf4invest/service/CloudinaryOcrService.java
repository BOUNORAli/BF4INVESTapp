package com.bf4invest.service;

import com.bf4invest.dto.OcrExtractResult;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CloudinaryOcrService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.folder.factures:bf4/factures}")
    private String facturesFolder;

    private Cloudinary buildClient() {
        if (StringUtils.isAnyBlank(cloudName, apiKey, apiSecret)) {
            throw new IllegalStateException("Configuration Cloudinary manquante (cloud name / api key / api secret)");
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    /**
     * Upload une image vers Cloudinary avec OCR et retourne les résultats extraits
     */
    public OcrExtractResult uploadAndExtract(MultipartFile file) throws IOException {
        log.info("🔍 [OCR] Début upload et extraction OCR - Fichier: {}, Taille: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        try {
            Cloudinary client = buildClient();

            // Upload avec paramètre OCR
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", facturesFolder + "/ocr",
                    "resource_type", "image",
                    "ocr", "adv_ocr:document", // Utiliser adv_ocr:document pour les documents texte-heavy
                    "overwrite", true
            );

            log.info("📤 [OCR] Upload vers Cloudinary avec OCR...");
            Map uploadResult = client.uploader().upload(file.getBytes(), uploadParams);

            // Extraire les données OCR
            String ocrText = extractOcrText(uploadResult);
            log.info("✅ [OCR] Texte extrait ({} caractères)", ocrText != null ? ocrText.length() : 0);

            if (ocrText == null || ocrText.trim().isEmpty()) {
                log.warn("⚠️ [OCR] Aucun texte détecté dans l'image");
                return OcrExtractResult.builder()
                        .rawText("")
                        .lignes(new ArrayList<>())
                        .confidence(0.0)
                        .build();
            }

            // Parser le texte pour extraire les informations structurées
            OcrExtractResult result = parseOcrText(ocrText);
            result.setRawText(ocrText);

            log.info("✅ [OCR] Parsing terminé - {} lignes détectées, Fournisseur: {}, Date: {}", 
                    result.getLignes().size(), 
                    result.getFournisseurNom(), 
                    result.getDateDocument());

            return result;

        } catch (Exception e) {
            log.error("❌ [OCR] Erreur lors de l'upload/extraction OCR", e);
            throw new IOException("Erreur lors de l'extraction OCR: " + e.getMessage(), e);
        }
    }

    /**
     * Extrait le texte depuis la réponse Cloudinary OCR
     */
    private String extractOcrText(Map<String, Object> uploadResult) {
        try {
            // Structure de réponse OCR Cloudinary
            Map<String, Object> info = (Map<String, Object>) uploadResult.get("info");
            if (info == null) {
                log.warn("⚠️ [OCR] Pas de section 'info' dans la réponse");
                return null;
            }

            Map<String, Object> ocr = (Map<String, Object>) info.get("ocr");
            if (ocr == null) {
                log.warn("⚠️ [OCR] Pas de section 'ocr' dans la réponse");
                return null;
            }

            Map<String, Object> advOcr = (Map<String, Object>) ocr.get("adv_ocr");
            if (advOcr == null) {
                log.warn("⚠️ [OCR] Pas de section 'adv_ocr' dans la réponse");
                return null;
            }

            String status = (String) advOcr.get("status");
            if (!"complete".equals(status)) {
                log.warn("⚠️ [OCR] Statut OCR: {} (attendu: complete)", status);
                return null;
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) advOcr.get("data");
            if (data == null || data.isEmpty()) {
                log.warn("⚠️ [OCR] Pas de données OCR dans la réponse");
                return null;
            }

            // Extraire le texte de la première annotation (qui contient tout le texte)
            StringBuilder fullText = new StringBuilder();
            for (Map<String, Object> block : data) {
                List<Map<String, Object>> textAnnotations = (List<Map<String, Object>>) block.get("textAnnotations");
                if (textAnnotations != null && !textAnnotations.isEmpty()) {
                    // La première annotation contient tout le texte
                    Map<String, Object> firstAnnotation = textAnnotations.get(0);
                    String description = (String) firstAnnotation.get("description");
                    if (description != null) {
                        fullText.append(description).append("\n");
                    }
                }
            }

            return fullText.toString().trim();

        } catch (Exception e) {
            log.error("❌ [OCR] Erreur lors de l'extraction du texte OCR", e);
            return null;
        }
    }

    /**
     * Parse le texte OCR pour extraire les informations structurées
     */
    private OcrExtractResult parseOcrText(String ocrText) {
        OcrExtractResult.OcrExtractResultBuilder builder = OcrExtractResult.builder();
        builder.confidence(0.8); // Score par défaut

        String[] lines = ocrText.split("\n");

        // Détecter le fournisseur (généralement dans les premières lignes)
        builder.fournisseurNom(detectFournisseur(lines));

        // Détecter la date
        builder.dateDocument(detectDate(lines));

        // Détecter le numéro de document
        builder.numeroDocument(detectNumeroDocument(lines));

        // Extraire les lignes de produits
        List<OcrExtractResult.OcrProductLine> productLines = extractProductLines(lines);
        builder.lignes(productLines);

        return builder.build();
    }

    /**
     * Détecte le nom du fournisseur (recherche dans les premières lignes)
     */
    private String detectFournisseur(String[] lines) {
        // Chercher dans les 10 premières lignes
        int maxLines = Math.min(10, lines.length);
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i].trim();
            // Ignorer les lignes courtes ou contenant des mots clés non pertinents
            if (line.length() > 5 && 
                !line.matches(".*(FACTURE|BON|COMMANDE|DATE|TOTAL|HT|TTC|ICE|RC).*")) {
                // Prendre la première ligne substantielle comme fournisseur
                return line;
            }
        }
        return null;
    }

    /**
     * Détecte la date dans le texte
     */
    private String detectDate(String[] lines) {
        // Patterns de dates courants: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd
        Pattern[] datePatterns = {
            Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b"),
            Pattern.compile("\\b(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\b")
        };

        for (String line : lines) {
            for (Pattern pattern : datePatterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String dateStr = matcher.group(1);
                    // Normaliser au format yyyy-MM-dd
                    try {
                        if (dateStr.contains("/")) {
                            String[] parts = dateStr.split("/");
                            if (parts.length == 3) {
                                if (parts[2].length() == 2) {
                                    parts[2] = "20" + parts[2];
                                }
                                return String.format("%s-%s-%s", parts[2], 
                                    String.format("%02d", Integer.parseInt(parts[1])),
                                    String.format("%02d", Integer.parseInt(parts[0])));
                            }
                        } else if (dateStr.contains("-")) {
                            return dateStr; // Déjà au bon format potentiellement
                        }
                    } catch (Exception e) {
                        log.debug("Erreur parsing date: {}", dateStr, e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Détecte le numéro de document (facture, BC, etc.)
     */
    private String detectNumeroDocument(String[] lines) {
        // Patterns: N° FACTURE, REF, NUM, etc.
        Pattern[] patterns = {
            Pattern.compile("(?i)(?:N°|NUM|REF|N°\\s*)?(?:FACTURE|BC|COMMANDE|DOC)?\\s*[:\\s]*([A-Z0-9\\-/]+)"),
            Pattern.compile("(?i)(?:Facture|BC|Commande)\\s*(?:N°|No|#)?\\s*[:\\s]*([A-Z0-9\\-/]+)")
        };

        for (String line : lines) {
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        }
        return null;
    }

    /**
     * Extrait les lignes de produits du texte OCR
     */
    private List<OcrExtractResult.OcrProductLine> extractProductLines(String[] lines) {
        List<OcrExtractResult.OcrProductLine> productLines = new ArrayList<>();
        
        // Patterns pour détecter les lignes de produits
        // Format typique: Désignation | Qté | PU | Total
        // Ou: Désignation Qté PU Total
        
        DecimalFormat df = new DecimalFormat("#,##0.00", 
            DecimalFormatSymbols.getInstance(Locale.FRANCE));

        boolean inProductSection = false;
        int consecutiveNumericLines = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Détecter le début de la section produits (mots clés)
            if (line.matches("(?i).*(?:DESIGNATION|ARTICLE|PRODUIT|LIBELLE).*") ||
                line.matches("(?i).*(?:QUANTITE|QTÉ|QTE).*")) {
                inProductSection = true;
                continue;
            }

            // Ignorer les lignes d'en-tête ou de totaux
            if (line.matches("(?i).*(?:TOTAL|SOUS-TOTAL|HT|TTC|TVA|REMISE).*")) {
                inProductSection = false;
                continue;
            }

            if (!inProductSection) {
                // Chercher des patterns numériques qui suggèrent une ligne de produit
                if (hasNumericValues(line)) {
                    consecutiveNumericLines++;
                    if (consecutiveNumericLines >= 2) {
                        inProductSection = true;
                    }
                } else {
                    consecutiveNumericLines = 0;
                }
            }

            if (inProductSection) {
                OcrExtractResult.OcrProductLine productLine = parseProductLine(line);
                if (productLine != null && productLine.getDesignation() != null && 
                    productLine.getQuantite() != null && productLine.getQuantite() > 0) {
                    productLines.add(productLine);
                    consecutiveNumericLines = 0; // Reset après une ligne valide
                }
            }
        }

        log.info("📦 [OCR] {} lignes de produits extraites", productLines.size());
        return productLines;
    }

    /**
     * Parse une ligne pour extraire les informations d'un produit
     */
    private OcrExtractResult.OcrProductLine parseProductLine(String line) {
        // Pattern pour détecter: désignation, puis nombres (qté, prix unitaire, total)
        // Exemple: "CIMENT CPJ 45 100 85.00 8500.00"
        
        // Séparer par espaces multiples ou tabs
        String[] parts = line.split("\\s{2,}|\t");
        if (parts.length < 2) {
            parts = line.split("\\s+");
        }

        if (parts.length < 2) {
            return null;
        }

        OcrExtractResult.OcrProductLine.OcrProductLineBuilder builder = 
            OcrExtractResult.OcrProductLine.builder();

        // Les premiers éléments sont généralement la désignation
        List<String> designationParts = new ArrayList<>();
        List<Double> numericValues = new ArrayList<>();

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // Essayer de parser comme nombre
            Double numValue = parseNumber(part);
            if (numValue != null) {
                numericValues.add(numValue);
            } else {
                designationParts.add(part);
            }
        }

        // Construire la désignation
        if (!designationParts.isEmpty()) {
            builder.designation(String.join(" ", designationParts));
        }

        // Assigner les valeurs numériques
        // Supposons: [qté, prix unitaire, total]
        if (numericValues.size() >= 1) {
            builder.quantite(numericValues.get(0));
        }
        if (numericValues.size() >= 2) {
            builder.prixUnitaireHT(numericValues.get(1));
        }
        if (numericValues.size() >= 3) {
            builder.prixTotalHT(numericValues.get(2));
        } else if (numericValues.size() >= 2) {
            // Si seulement 2 valeurs, calculer le total si possible
            if (numericValues.get(0) != null && numericValues.get(1) != null) {
                builder.prixTotalHT(numericValues.get(0) * numericValues.get(1));
            }
        }

        // Déterminer l'unité (U par défaut)
        builder.unite("U");

        OcrExtractResult.OcrProductLine result = builder.build();
        
        // Valider que la ligne contient au moins une désignation et une quantité
        if (result.getDesignation() == null || result.getDesignation().trim().isEmpty()) {
            return null;
        }
        if (result.getQuantite() == null || result.getQuantite() <= 0) {
            return null;
        }

        return result;
    }

    /**
     * Parse un nombre depuis une chaîne (gère les formats français avec virgule)
     */
    private Double parseNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }

        try {
            // Retirer les espaces
            str = str.replaceAll("\\s", "");
            
            // Remplacer la virgule par un point pour le parsing
            str = str.replace(",", ".");
            
            // Retirer les caractères non numériques sauf point et moins
            str = str.replaceAll("[^0-9.\\-]", "");
            
            if (str.isEmpty()) {
                return null;
            }

            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Vérifie si une ligne contient des valeurs numériques (suggère une ligne de produit)
     */
    private boolean hasNumericValues(String line) {
        // Chercher au moins 2 nombres dans la ligne
        Pattern numberPattern = Pattern.compile("\\b\\d+[.,]?\\d*\\b");
        Matcher matcher = numberPattern.matcher(line);
        int count = 0;
        while (matcher.find() && count < 3) {
            count++;
        }
        return count >= 2;
    }
}

