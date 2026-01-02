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

            log.info("✅ [OCR] Parsing terminé - {} lignes détectées, Fournisseur: {}, Date: {}, N°Doc: {}", 
                    result.getLignes().size(), 
                    result.getFournisseurNom() != null ? result.getFournisseurNom() : "non détecté",
                    result.getDateDocument() != null ? result.getDateDocument() : "non détectée",
                    result.getNumeroDocument() != null ? result.getNumeroDocument() : "non détecté");
            
            // Log les premières lignes du texte OCR pour débogage
            if (result.getLignes().isEmpty()) {
                log.warn("⚠️ [OCR] Aucun produit détecté. Premières lignes du texte OCR:");
                String[] lines = ocrText.split("\n");
                int maxLines = Math.min(20, lines.length);
                for (int i = 0; i < maxLines; i++) {
                    if (lines[i].trim().length() > 0) {
                        log.warn("  Ligne {}: {}", i + 1, lines[i].trim());
                    }
                }
            }

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
        // Chercher dans les 15 premières lignes
        int maxLines = Math.min(15, lines.length);
        
        // Chercher des patterns comme "Raison Sociale:", "Fournisseur:", ou un nom en majuscules en haut
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i].trim();
            
            // Pattern: "Raison Sociale: NOM" ou "Fournisseur: NOM"
            if (line.matches("(?i).*(?:RAISON\\s+SOCIALE|FOURNISSEUR|CLIENT|STE|SARL|EURL).*[:]\\s*(.+)")) {
                Pattern pattern = Pattern.compile("(?i).*(?:RAISON\\s+SOCIALE|FOURNISSEUR|CLIENT|STE|SARL|EURL).*[:]\\s*(.+)");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String name = matcher.group(1).trim();
                    if (name.length() > 3) {
                        return name;
                    }
                }
            }
            
            // Chercher un nom d'entreprise en majuscules (SARL, STE, etc.)
            if (line.matches(".*[A-Z]{3,}.*(?:SARL|STE|EURL|SA).*")) {
                // Nettoyer la ligne
                line = line.replaceAll("(?i)(SIE|SIEGE|SOCIAL|ADRESSE|TEL|FAX|ICE|IF|RC|CNSS).*", "");
                line = line.trim();
                if (line.length() > 5 && line.length() < 100) {
                    return line;
                }
            }
        }
        
        // Fallback: prendre la première ligne substantielle qui ne contient pas de mots clés
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i].trim();
            if (line.length() > 5 && line.length() < 80 &&
                !line.matches("(?i).*(?:FACTURE|BON|COMMANDE|DATE|TOTAL|HT|TTC|ICE|RC|CNSS|TEL|FAX|ADRESSE|SIE|SIEGE).*") &&
                !line.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) { // Pas une date
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
        
        DecimalFormat df = new DecimalFormat("#,##0.00", 
            DecimalFormatSymbols.getInstance(Locale.FRANCE));

        boolean inProductSection = false;
        boolean foundHeader = false;
        int consecutiveNumericLines = 0;
        int lineIndex = 0;

        // Chercher l'en-tête de la section produits
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toUpperCase();
            // Détecter l'en-tête avec plusieurs colonnes (Désignation, Qté, Prix unitaire, Montant HT)
            if (line.contains("DESIGNATION") && (line.contains("QTÉ") || line.contains("QTE") || line.contains("QUANTITE"))) {
                inProductSection = true;
                foundHeader = true;
                lineIndex = i + 1;
                log.debug("🎯 [OCR] En-tête produits détecté à la ligne {}", i);
                break;
            }
        }

        // Si on n'a pas trouvé d'en-tête, essayer une détection heuristique
        if (!foundHeader) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.matches("(?i).*(?:DESIGNATION|ARTICLE|PRODUIT|LIBELLE).*") ||
                    line.matches("(?i).*(?:QUANTITE|QTÉ|QTE).*")) {
                    inProductSection = true;
                    lineIndex = i + 1;
                    log.debug("🎯 [OCR] Section produits détectée (heuristique) à la ligne {}", i);
                    break;
                }
            }
        }

        // Parcourir les lignes après l'en-tête
        for (int i = lineIndex; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                consecutiveNumericLines = 0;
                continue;
            }

            // Arrêter si on atteint une section de totaux
            if (line.matches("(?i).*TOTAL\\s+(HT|TTC).*") || 
                line.matches("(?i).*T\\.V\\.A.*") ||
                line.matches("(?i).*TOTAL\\s+[A-Z].*")) {
                log.debug("🛑 [OCR] Section totaux détectée à la ligne {}, arrêt de l'extraction", i);
                break;
            }

            // Ignorer les lignes qui sont clairement des totaux ou notes
            if (line.matches("(?i).*(?:ARRÊTER|ARRETE|IMPORTANT|CONFORMEMENT).*")) {
                break;
            }

            // Essayer de parser la ligne comme une ligne de produit
            OcrExtractResult.OcrProductLine productLine = parseProductLine(line);
            if (productLine != null && productLine.getDesignation() != null && 
                productLine.getDesignation().trim().length() > 2 && // Désignation doit avoir au moins 3 caractères
                productLine.getQuantite() != null && productLine.getQuantite() > 0) {
                productLines.add(productLine);
                consecutiveNumericLines = 0;
                log.debug("✅ [OCR] Produit détecté: {} - Qté: {} - PU: {}", 
                    productLine.getDesignation(), productLine.getQuantite(), productLine.getPrixUnitaireHT());
            } else if (hasNumericValues(line)) {
                // Si la ligne contient des nombres mais n'a pas pu être parsée, on continue
                consecutiveNumericLines++;
                if (consecutiveNumericLines > 5) {
                    // Si 5 lignes consécutives avec nombres mais non parsables, on s'arrête
                    log.debug("🛑 [OCR] Trop de lignes non parsables, arrêt à la ligne {}", i);
                    break;
                }
            } else {
                consecutiveNumericLines = 0;
            }
        }

        log.info("📦 [OCR] {} lignes de produits extraites", productLines.size());
        return productLines;
    }

    /**
     * Parse une ligne pour extraire les informations d'un produit
     * Gère les formats: "DIAM 8 HB UNIVERS ACIER    200,000    8,083    1 616,67"
     *                  "CIMENT CPJ 45    23 500,000    1,452    34 125,92"
     */
    private OcrExtractResult.OcrProductLine parseProductLine(String line) {
        // Essayer d'abord de détecter des colonnes séparées par plusieurs espaces ou tabs
        String[] parts = line.split("\\s{3,}|\t+"); // Au moins 3 espaces ou tabs
        
        // Si pas de colonnes clairement séparées, essayer avec 2 espaces
        if (parts.length < 3) {
            parts = line.split("\\s{2,}");
        }
        
        // Si toujours pas assez de colonnes, essayer avec 1 espace
        if (parts.length < 3) {
            parts = line.split("\\s+");
        }

        if (parts.length < 2) {
            return null;
        }

        OcrExtractResult.OcrProductLine.OcrProductLineBuilder builder = 
            OcrExtractResult.OcrProductLine.builder();

        // Identifier les parties: désignation (texte) et nombres
        List<String> designationParts = new ArrayList<>();
        List<Double> numericValues = new ArrayList<>();
        boolean foundFirstNumber = false;

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // Essayer de parser comme nombre
            Double numValue = parseNumber(part);
            if (numValue != null && numValue > 0) {
                numericValues.add(numValue);
                foundFirstNumber = true;
            } else if (!foundFirstNumber) {
                // Avant le premier nombre, c'est probablement la désignation
                // Ignorer les mots trop courts qui sont probablement du bruit
                if (part.length() > 1 && !part.matches("^[^a-zA-Z]*$")) {
                    designationParts.add(part);
                }
            }
        }

        // Si on n'a pas trouvé de nombres, la ligne n'est probablement pas un produit
        if (numericValues.isEmpty()) {
            return null;
        }

        // Construire la désignation (même si vide, on essaiera de la compléter plus tard)
        String designation = designationParts.isEmpty() ? "" : String.join(" ", designationParts);
        
        // Si la désignation est vide ou trop courte, essayer de la récupérer du début de la ligne
        if (designation.trim().isEmpty() || designation.trim().length() < 3) {
            // Extraire tout le texte avant le premier nombre
            Pattern firstNumberPattern = Pattern.compile("^(.*?)(\\d+[.,]?\\d*\\s*\\d*[.,]?\\d*)");
            Matcher matcher = firstNumberPattern.matcher(line);
            if (matcher.find()) {
                String beforeNumber = matcher.group(1).trim();
                // Nettoyer la désignation (retirer caractères spéciaux en fin)
                beforeNumber = beforeNumber.replaceAll("[^a-zA-Z0-9\\s\\-]+$", "");
                if (beforeNumber.length() >= 3) {
                    designation = beforeNumber;
                }
            }
        }
        
        builder.designation(designation.trim());

        // Assigner les valeurs numériques selon leur position
        // Format typique: [qté, prix unitaire, montant HT]
        // Parfois: [qté, prix unitaire] (on calculera le total)
        if (numericValues.size() >= 1) {
            // La première valeur est généralement la quantité
            Double qte = numericValues.get(0);
            // Si la quantité semble trop grande pour être un prix unitaire (> 10000), 
            // et qu'on a plus de valeurs, la première pourrait être la quantité
            if (numericValues.size() >= 2) {
                builder.quantite(qte);
                builder.prixUnitaireHT(numericValues.get(1));
            } else {
                // Une seule valeur, c'est probablement la quantité
                builder.quantite(qte);
            }
        }
        
        if (numericValues.size() >= 3) {
            // Trois valeurs: qté, prix unitaire, montant HT
            builder.quantite(numericValues.get(0));
            builder.prixUnitaireHT(numericValues.get(1));
            builder.prixTotalHT(numericValues.get(2));
        } else if (numericValues.size() == 2) {
            // Deux valeurs: calculer le montant total si possible
            builder.quantite(numericValues.get(0));
            builder.prixUnitaireHT(numericValues.get(1));
            // Calculer le total si on a qté et PU
            if (numericValues.get(0) > 0 && numericValues.get(1) > 0) {
                builder.prixTotalHT(numericValues.get(0) * numericValues.get(1));
            }
        }

        // Déterminer l'unité (U par défaut)
        builder.unite("U");

        OcrExtractResult.OcrProductLine result = builder.build();
        
        // Valider que la ligne contient au moins une désignation et une quantité
        if (result.getDesignation() == null || result.getDesignation().trim().isEmpty() || result.getDesignation().trim().length() < 2) {
            return null;
        }
        if (result.getQuantite() == null || result.getQuantite() <= 0) {
            return null;
        }

        return result;
    }

    /**
     * Parse un nombre depuis une chaîne (gère les formats français avec virgule)
     * Gère: "1 616,67", "23 500,000", "8,083", "200,000"
     */
    private Double parseNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }

        try {
            // Nettoyer la chaîne
            str = str.trim();
            
            // Gérer les espaces comme séparateurs de milliers
            // Exemple: "1 616,67" -> "1616,67"
            // Ne retirer les espaces que s'ils sont entre des chiffres (pas après une virgule)
            str = str.replaceAll("(\\d)\\s+(\\d)", "$1$2"); // Espace entre chiffres = séparateur de milliers
            
            // Remplacer la virgule par un point pour le parsing
            str = str.replace(",", ".");
            
            // Retirer les caractères non numériques sauf point et moins
            str = str.replaceAll("[^0-9.\\-]", "");
            
            if (str.isEmpty() || str.equals("-") || str.equals(".")) {
                return null;
            }

            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            log.debug("Erreur parsing nombre: '{}'", str);
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

