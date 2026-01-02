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
        log.debug("🔧 [OCR] Construction client Cloudinary - cloudName: {}, apiKey: {}, apiSecret: {}", 
                cloudName != null && !cloudName.isEmpty() ? "présent" : "manquant",
                apiKey != null && !apiKey.isEmpty() ? "présent" : "manquant",
                apiSecret != null && !apiSecret.isEmpty() ? "présent" : "manquant");
        
        if (StringUtils.isAnyBlank(cloudName, apiKey, apiSecret)) {
            String missing = "";
            if (StringUtils.isBlank(cloudName)) missing += "cloud-name ";
            if (StringUtils.isBlank(apiKey)) missing += "api-key ";
            if (StringUtils.isBlank(apiSecret)) missing += "api-secret ";
            throw new IllegalStateException("Configuration Cloudinary manquante: " + missing.trim());
        }
        
        try {
            Cloudinary client = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true
            ));
            log.debug("✅ [OCR] Client Cloudinary construit avec succès");
            return client;
        } catch (Exception e) {
            log.error("❌ [OCR] Erreur lors de la construction du client Cloudinary", e);
            throw new IllegalStateException("Impossible de construire le client Cloudinary: " + e.getMessage(), e);
        }
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
            // Note: L'OCR peut être asynchrone selon la configuration Cloudinary
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", facturesFolder + "/ocr",
                    "resource_type", "image",
                    "ocr", "adv_ocr", // Format standard pour OCR
                    "overwrite", true
            );
            
            log.debug("📤 [OCR] Paramètres d'upload: {}", uploadParams);

            log.info("📤 [OCR] Upload vers Cloudinary avec OCR...");
            Map uploadResult = client.uploader().upload(file.getBytes(), uploadParams);

            // Log la structure complète de la réponse pour débogage
            log.info("📋 [OCR] Structure réponse Cloudinary - Clés principales: {}", uploadResult.keySet());
            if (uploadResult.containsKey("info")) {
                Map<String, Object> info = (Map<String, Object>) uploadResult.get("info");
                log.info("📋 [OCR] Structure 'info' - Clés: {}", info != null ? info.keySet() : "null");
                if (info != null && info.containsKey("ocr")) {
                    Map<String, Object> ocr = (Map<String, Object>) info.get("ocr");
                    log.info("📋 [OCR] Structure 'ocr' - Clés: {}", ocr != null ? ocr.keySet() : "null");
                    if (ocr != null) {
                        // Logger chaque clé dans ocr pour voir ce qui est disponible
                        for (String key : ocr.keySet()) {
                            Object value = ocr.get(key);
                            if (value instanceof Map) {
                                Map<String, Object> ocrMap = (Map<String, Object>) value;
                                log.info("📋 [OCR] Clé 'ocr.{}' - Type: Map, Clés: {}", key, ocrMap.keySet());
                            } else {
                                log.info("📋 [OCR] Clé 'ocr.{}' - Type: {}, Valeur: {}", key, 
                                    value != null ? value.getClass().getSimpleName() : "null", 
                                    value instanceof String ? ((String) value).substring(0, Math.min(100, ((String) value).length())) : value);
                            }
                        }
                    }
                }
            }

            // Extraire les données OCR
            String ocrText = extractOcrText(uploadResult);
            log.info("✅ [OCR] Texte extrait ({} caractères)", ocrText != null ? ocrText.length() : 0);
            
            // Si aucun texte n'a été extrait, logger plus de détails
            if (ocrText == null || ocrText.isEmpty()) {
                log.warn("⚠️ [OCR] Aucun texte extrait. Réponse complète (premiers 2000 caractères): {}", 
                    uploadResult.toString().substring(0, Math.min(2000, uploadResult.toString().length())));
            }

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

        } catch (IllegalStateException e) {
            // Configuration manquante - relancer telle quelle
            log.error("❌ [OCR] Configuration Cloudinary manquante", e);
            throw e;
        } catch (Exception e) {
            log.error("❌ [OCR] Erreur lors de l'upload/extraction OCR", e);
            log.error("❌ [OCR] Type d'exception: {}, Message: {}", e.getClass().getName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("❌ [OCR] Cause: {}", e.getCause().getMessage());
            }
            // Stack trace complet pour débogage
            log.error("❌ [OCR] Stack trace:", e);
            throw new IOException("Erreur lors de l'extraction OCR: " + e.getMessage(), e);
        }
    }

    /**
     * Extrait le texte depuis la réponse Cloudinary OCR
     * Gère différentes structures de réponse Cloudinary
     */
    private String extractOcrText(Map<String, Object> uploadResult) {
        try {
            // Structure de réponse OCR Cloudinary - plusieurs formats possibles
            
            // Format 1: info.ocr.adv_ocr (standard)
            Map<String, Object> info = (Map<String, Object>) uploadResult.get("info");
            if (info != null) {
                Map<String, Object> ocr = (Map<String, Object>) info.get("ocr");
                if (ocr != null) {
                    // Essayer adv_ocr
                    Map<String, Object> advOcr = (Map<String, Object>) ocr.get("adv_ocr");
                    if (advOcr != null) {
                        String text = extractTextFromAdvOcr(advOcr);
                        if (text != null && !text.isEmpty()) {
                            return text;
                        }
                    }
                    
                    // Essayer d'autres formats possibles dans ocr
                    for (String key : ocr.keySet()) {
                        log.debug("🔍 [OCR] Clé trouvée dans 'ocr': {}", key);
                        Object value = ocr.get(key);
                        if (value instanceof Map) {
                            String text = extractTextFromAdvOcr((Map<String, Object>) value);
                            if (text != null && !text.isEmpty()) {
                                return text;
                            }
                        }
                    }
                }
            }

            // Format 2: OCR directement dans la réponse (peut-être dans pages ou autre)
            // Essayer de trouver n'importe quelle structure contenant du texte
            String textFromDirect = extractTextFromMap(uploadResult);
            if (textFromDirect != null && !textFromDirect.isEmpty()) {
                return textFromDirect;
            }

            // Format 3: Vérifier si l'OCR est asynchrone et nécessite un polling
            // Pour l'instant, on retourne null et on log la structure complète
            log.warn("⚠️ [OCR] Aucun texte OCR trouvé. Structure réponse complète: {}", uploadResult.keySet());
            
            return null;

        } catch (Exception e) {
            log.error("❌ [OCR] Erreur lors de l'extraction du texte OCR", e);
            return null;
        }
    }

    /**
     * Extrait le texte depuis une structure adv_ocr
     */
    private String extractTextFromAdvOcr(Map<String, Object> advOcr) {
        try {
            String status = (String) advOcr.get("status");
            if (status != null && !"complete".equals(status)) {
                log.debug("⚠️ [OCR] Statut OCR: {} (attendu: complete)", status);
                // Si le statut est "pending", l'OCR est peut-être asynchrone
                if ("pending".equals(status)) {
                    log.warn("⚠️ [OCR] OCR en cours de traitement (statut: pending). L'OCR peut être asynchrone.");
                }
                return null;
            }

            // Chercher les données dans différents formats possibles
            Object dataObj = advOcr.get("data");
            if (dataObj == null) {
                return null;
            }

            StringBuilder fullText = new StringBuilder();

            if (dataObj instanceof List) {
                // Format: List<Map> avec textAnnotations
                List<Map<String, Object>> data = (List<Map<String, Object>>) dataObj;
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
            } else if (dataObj instanceof Map) {
                // Format alternatif: Map direct
                Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                if (dataMap.containsKey("text")) {
                    return (String) dataMap.get("text");
                }
                if (dataMap.containsKey("fullTextAnnotation")) {
                    Map<String, Object> fullTextAnnotation = (Map<String, Object>) dataMap.get("fullTextAnnotation");
                    if (fullTextAnnotation != null && fullTextAnnotation.containsKey("text")) {
                        return (String) fullTextAnnotation.get("text");
                    }
                }
            }

            return fullText.length() > 0 ? fullText.toString().trim() : null;

        } catch (Exception e) {
            log.error("❌ [OCR] Erreur lors de l'extraction depuis adv_ocr", e);
            return null;
        }
    }

    /**
     * Essaie d'extraire du texte depuis une Map quelconque (récursif)
     */
    private String extractTextFromMap(Map<String, Object> map) {
        try {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if ("text".equals(entry.getKey()) || "description".equals(entry.getKey())) {
                    if (entry.getValue() instanceof String) {
                        String text = (String) entry.getValue();
                        if (text != null && text.trim().length() > 10) {
                            return text;
                        }
                    }
                } else if (entry.getValue() instanceof Map) {
                    String text = extractTextFromMap((Map<String, Object>) entry.getValue());
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                } else if (entry.getValue() instanceof List) {
                    List<?> list = (List<?>) entry.getValue();
                    for (Object item : list) {
                        if (item instanceof Map) {
                            String text = extractTextFromMap((Map<String, Object>) item);
                            if (text != null && !text.isEmpty()) {
                                return text;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs de parcours récursif
        }
        return null;
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
     * Amélioré pour mieux détecter "GUARIMETAL sarl" et éviter le client
     */
    private String detectFournisseur(String[] lines) {
        // Chercher dans les 15 premières lignes
        int maxLines = Math.min(15, lines.length);
        
        // Priorité 1: Chercher explicitement "Fournisseur:" (peu commun mais plus sûr)
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i].trim();
            if (line.matches("(?i).*FOURNISSEUR.*[:]\\s*(.+)")) {
                Pattern pattern = Pattern.compile("(?i).*FOURNISSEUR.*[:]\\s*(.+)");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String name = matcher.group(1).trim();
                    if (name.length() > 3) {
                        log.debug("🏢 [OCR] Fournisseur détecté (mot-clé 'Fournisseur'): {}", name);
                        return name;
                    }
                }
            }
        }
        
        // Priorité 2: Chercher le nom de l'entreprise émettrice (en haut, souvent avec logo)
        // Généralement AVANT la section "Client" et contient "SARL", "STE", etc. en minuscules ou majuscules
        boolean foundClientSection = false;
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i].trim();
            
            // Si on trouve "Client", on est dans la section client, donc le fournisseur est avant
            if (line.matches("(?i).*^CLIENT$.*")) {
                foundClientSection = true;
                break;
            }
            
            // Chercher un nom d'entreprise avec SARL, STE, etc. (peut être en minuscules)
            // Exemple: "GUARIMETAL sarl"
            if (line.matches("(?i).*[A-Z]{2,}.*(?:SARL|STE|EURL|SA).*")) {
                // Nettoyer la ligne (retirer activités, adresse, tel, etc.)
                String cleaned = line;
                // Retirer les activités (ex: "Vente de matériaux...")
                cleaned = cleaned.replaceAll("(?i)(Vente|Import|Export|Transport|Travaux|Construction).*", "");
                // Retirer adresse et coordonnées
                cleaned = cleaned.replaceAll("(?i)(SIE|SIEGE|SOCIAL|ADRESSE|TEL|FAX|ICE|IF|RC|CNSS|PATENTE|Z\\.|B\\.P|BP|INDUSTRIELLE).*", "");
                cleaned = cleaned.trim();
                
                // Ne pas prendre si c'est trop court ou contient des coordonnées
                if (cleaned.length() > 5 && cleaned.length() < 100 && 
                    !cleaned.matches(".*\\d{2}\\s*/\\s*\\d{2}.*") && // Pas une date
                    !cleaned.matches(".*\\d{5,}.*")) { // Pas un code postal ou ICE
                    log.debug("🏢 [OCR] Fournisseur détecté (nom entreprise en haut): {}", cleaned);
                    return cleaned;
                }
            }
        }
        
        // Si on a trouvé la section client, on sait que le fournisseur est avant
        // Chercher dans les lignes avant "Client"
        if (foundClientSection) {
            for (int i = 0; i < maxLines; i++) {
                String line = lines[i].trim();
                if (line.matches("(?i).*CLIENT.*")) {
                    break; // On a atteint la section client
                }
                // Chercher un nom d'entreprise (même sans SARL visible)
                if (line.matches(".*[A-Z]{4,}.*") && line.length() < 80 && 
                    !line.matches("(?i).*(?:FACTURE|BON|COMMANDE|DATE|TOTAL|HT|TTC|ICE|RC|CNSS|TEL|FAX|ADRESSE|SIE|SIEGE|Z\\.|B\\.P|BP).*")) {
                    String cleaned = line.trim();
                    if (cleaned.length() > 5 && cleaned.length() < 80) {
                        log.debug("🏢 [OCR] Fournisseur détecté (avant section Client): {}", cleaned);
                        return cleaned;
                    }
                }
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
     * Amélioré pour mieux détecter les numéros comme "000002366"
     */
    private String detectNumeroDocument(String[] lines) {
        // Patterns: N° FACTURE, REF, NUM, etc.
        Pattern[] patterns = {
            // Pattern pour "FACTURE N° 000002366" ou "FACTURE N°: 000002366"
            Pattern.compile("(?i)(?:FACTURE|BC|COMMANDE|DOC)\\s*(?:N°|No|NUM|NUMERO|REF|REFERENCE)?\\s*[:\\s]*([A-Z0-9\\-/]+)"),
            // Pattern pour "N° FACTURE: 000002366"
            Pattern.compile("(?i)(?:N°|NUM|REF|N°\\s*)?(?:FACTURE|BC|COMMANDE|DOC)?\\s*[:\\s]*([A-Z0-9\\-/]+)"),
            // Pattern spécifique pour "FACTURE N°" suivi d'un numéro long (ex: 000002366)
            Pattern.compile("(?i)FACTURE\\s*N°\\s*([0-9]{4,})"),
            // Pattern pour numéros longs avec zéros (000002366) dans une ligne contenant FACTURE
            Pattern.compile("(?i).*FACTURE.*?([0-9]{6,})")
        };

        for (String line : lines) {
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String numero = matcher.group(1).trim();
                    // Nettoyer si nécessaire (retirer caractères parasites)
                    numero = numero.replaceAll("[^A-Z0-9\\-/]", "");
                    if (numero.length() >= 3) {
                        log.debug("📄 [OCR] Numéro document détecté: {}", numero);
                        return numero;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extrait les lignes de produits du texte OCR
     * Amélioré pour mieux détecter la zone du tableau et filtrer les faux positifs
     */
    private List<OcrExtractResult.OcrProductLine> extractProductLines(String[] lines) {
        List<OcrExtractResult.OcrProductLine> productLines = new ArrayList<>();

        // Étape 1: Détecter le début du tableau
        int tableStartIndex = findTableStart(lines);
        if (tableStartIndex == -1) {
            log.warn("⚠️ [OCR] Début de tableau non détecté, utilisation du fallback");
            tableStartIndex = findTableStartFallback(lines);
        }

        // Étape 2: Détecter la fin du tableau
        int tableEndIndex = findTableEnd(lines, tableStartIndex);

        if (tableStartIndex == -1 || tableEndIndex <= tableStartIndex) {
            log.warn("⚠️ [OCR] Impossible de déterminer la zone du tableau");
            return productLines;
        }

        log.info("📋 [OCR] Zone tableau détectée: lignes {} à {}", tableStartIndex, tableEndIndex);

        // Étape 3: Parser les lignes dans la zone du tableau
        // Note: Dans certains formats OCR, la désignation peut être sur une ligne et les nombres sur la suivante
        for (int i = tableStartIndex; i < tableEndIndex; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // Filtrer les lignes de bruit (adresse, téléphone, etc.)
            if (isNoiseLine(line)) {
                log.debug("🚫 [OCR] Ligne ignorée (bruit): {}", line.substring(0, Math.min(50, line.length())));
                continue;
            }

            // Parser la ligne comme une ligne de produit
            // Essayer d'abord la ligne seule
            OcrExtractResult.OcrProductLine productLine = parseProductLine(line);
            
            // Si pas de produit détecté mais la ligne contient du texte (pas que des nombres),
            // essayer avec la ligne suivante pour les nombres
            // Format OCR typique: désignation sur une ligne, nombres sur la suivante
            if (productLine == null && i + 1 < tableEndIndex) {
                // Si la ligne actuelle contient principalement du texte (désignation)
                boolean isTextLine = line.matches(".*[A-Za-z]{3,}.*") && 
                                     !line.matches(".*\\d{4,}.*"); // Pas trop de chiffres
                
                // Si c'est une ligne texte, regarder la ligne suivante
                if (isTextLine) {
                    String nextLine = lines[i + 1].trim();
                    // Si la ligne suivante contient plusieurs nombres séparés (Qté, Prix, Total)
                    // Pattern pour 2+ nombres avec espaces ou virgules
                    if (nextLine.matches(".*\\d+[.,]?\\d*.*\\s+.*\\d+[.,]?\\d*.*")) {
                        // Combiner les deux lignes avec un séparateur clair
                        String combinedLine = line + "    " + nextLine; // Plusieurs espaces pour séparer
                        productLine = parseProductLine(combinedLine);
                        if (productLine != null && isValidProductLine(productLine)) {
                            i++; // Skip la ligne suivante car on l'a déjà utilisée
                        } else {
                            productLine = null; // Réinitialiser si invalide
                        }
                    }
                }
            }
            
            if (productLine != null && isValidProductLine(productLine)) {
                productLines.add(productLine);
                log.debug("✅ [OCR] Produit détecté: {} - Qté: {} - PU: {} - Total: {}", 
                    productLine.getDesignation(), 
                    productLine.getQuantite(), 
                    productLine.getPrixUnitaireHT(),
                    productLine.getPrixTotalHT());
            }
        }

        log.info("📦 [OCR] {} lignes de produits extraites", productLines.size());
        return productLines;
    }

    /**
     * Détecte le début du tableau de produits
     * Cherche les lignes contenant "Désignation" + "Qté" ou "Quantité" ou "Prix"
     */
    private int findTableStart(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toUpperCase();
            if (isTableHeader(line)) {
                log.debug("🎯 [OCR] En-tête tableau détecté à la ligne {}: {}", i, lines[i].trim());
                return i + 1; // Retourner la ligne suivante (après l'en-tête)
            }
        }
        return -1;
    }

    /**
     * Fallback: cherche le début du tableau sans en-tête clair
     * Cherche les premières lignes avec pattern de produit valide
     */
    private int findTableStartFallback(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (isNoiseLine(line)) {
                continue;
            }
            // Si la ligne ressemble à une ligne de produit (texte + nombres)
            if (hasNumericValues(line) && line.length() > 10) {
                // Vérifier qu'il y a au moins 2 nombres (qté + prix)
                Pattern numberPattern = Pattern.compile("\\b\\d+[.,\\s]?\\d*\\b");
                Matcher matcher = numberPattern.matcher(line);
                int count = 0;
                while (matcher.find() && count < 3) {
                    count++;
                }
                if (count >= 2) {
                    log.debug("🎯 [OCR] Début tableau détecté (fallback) à la ligne {}: {}", i, line.substring(0, Math.min(50, line.length())));
                    return i;
                }
            }
        }
        return 0; // Par défaut, commencer au début
    }

    /**
     * Détecte la fin du tableau de produits
     * Cherche les lignes contenant "TOTAL", "Total HT", "Sous-total", "T.V.A", etc.
     */
    private int findTableEnd(String[] lines, int startIndex) {
        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i].trim().toUpperCase();
            if (isTableEnd(line)) {
                log.debug("🛑 [OCR] Fin tableau détectée à la ligne {}: {}", i, lines[i].trim());
                return i;
            }
        }
        // Si pas de fin trouvée, arrêter avant la fin du document (dernières 10 lignes pour éviter le footer)
        return Math.max(startIndex + 1, lines.length - 10);
    }

    /**
     * Vérifie si une ligne est l'en-tête du tableau
     */
    private boolean isTableHeader(String line) {
        String upperLine = line.toUpperCase();
        boolean hasDesignation = upperLine.contains("DESIGNATION") || 
                                 upperLine.contains("DESCRIPTION") || 
                                 upperLine.contains("ARTICLE") || 
                                 upperLine.contains("PRODUIT") ||
                                 upperLine.contains("LIBELLE");
        
        boolean hasQuantity = upperLine.contains("QTÉ") || 
                             upperLine.contains("QTE") || 
                             upperLine.contains("QUANTITE") ||
                             upperLine.contains("QT");
        
        boolean hasPrice = upperLine.contains("PRIX") || 
                          upperLine.contains("MONTANT") ||
                          upperLine.contains("TOTAL");
        
        return hasDesignation && (hasQuantity || hasPrice);
    }

    /**
     * Vérifie si une ligne marque la fin du tableau
     */
    private boolean isTableEnd(String line) {
        String upperLine = line.toUpperCase();
        return upperLine.contains("TOTAL") && (upperLine.contains("HT") || upperLine.contains("TTC")) ||
               upperLine.contains("T.V.A") ||
               upperLine.contains("TVA") ||
               upperLine.contains("SOUS-TOTAL") ||
               upperLine.contains("SOUSTOTAL") ||
               upperLine.matches(".*TOTAL\\s+[A-Z]{2,}.*") ||
               upperLine.contains("ARRÊTER") ||
               upperLine.contains("ARRETE") ||
               upperLine.contains("IMPORTANT") ||
               upperLine.contains("CONFORMEMENT") ||
               upperLine.contains("MODE DE REGLEMENT");
    }

    /**
     * Vérifie si une ligne est du bruit (adresse, téléphone, etc.) et doit être ignorée
     */
    private boolean isNoiseLine(String line) {
        String upperLine = line.toUpperCase();
        
        // Mots-clés à ignorer
        String[] noiseKeywords = {
            "TEL", "FAX", "ADRESSE", "SIEGE", "SIÉGE", "SIE",
            "ICE", "IF", "R.C", "RC", "CNSS", "PATENTE",
            "RAISON SOCIALE", "CLIENT", "MODE DE REGLEMENT",
            "RIB", "B.P", "BP", "VILLE", "COPIE", "COPL",
            "SELOUANE", "NADOR", "MEKNES",
            "GUARIMETAL", "SARL", "SIE SOCIAL",
            "Z.INDUSTRIELLE", "ZONE INDUSTRIELLE",
            "RECEPTION", "SIGNATURE", "NOM",
            "DAHIR", "LOI", "PENALITE", "PENALITÉ",
            "IMPORTANT", "CONFORMEMENT", "DISPOSITIONS"
        };
        
        // Pattern pour les lignes qui commencent par "Tél:" ou "Fax:"
        if (upperLine.startsWith("TEL") || upperLine.startsWith("FAX") || 
            upperLine.contains("TEL:") || upperLine.contains("FAX:")) {
            return true;
        }
        
        // Pattern spécial pour les numéros de téléphone (plusieurs nombres séparés par / ou espace)
        // Exemple: "Tél: 05 36 35 89 60/05 36 60 94 34" ou "05 36 35 89 60/05 36 60 94 34"
        // Séquence de 2 chiffres répétée plusieurs fois = numéro de téléphone
        if (upperLine.matches(".*\\d{2}\\s+\\d{2}\\s+\\d{2}\\s+\\d{2}\\s+\\d{2}.*")) {
            return true; // C'est un numéro de téléphone
        }
        
        // Pattern pour les lignes qui contiennent un slash avec des nombres de chaque côté
        // (typique des numéros de téléphone multiples: "60/05")
        if (upperLine.matches(".*\\d{2,}.*/.*\\d{2,}.*")) {
            return true;
        }
        
        for (String keyword : noiseKeywords) {
            if (upperLine.contains(keyword)) {
                return true;
            }
        }
        
        // Ignorer les lignes trop courtes (< 5 caractères)
        if (line.trim().length() < 5) {
            return true;
        }
        
        // Ignorer les lignes qui sont uniquement des nombres ou des symboles
        if (line.trim().matches("^[0-9\\s\\.,\\-\\+/]+$")) {
            return true;
        }
        
        return false;
    }

    /**
     * Valide qu'une ligne de produit est valide
     */
    private boolean isValidProductLine(OcrExtractResult.OcrProductLine productLine) {
        if (productLine == null) {
            return false;
        }
        
        // Désignation doit avoir au moins 3 caractères
        if (productLine.getDesignation() == null || 
            productLine.getDesignation().trim().length() < 3) {
            return false;
        }
        
        // Doit avoir au moins une quantité > 0
        if (productLine.getQuantite() == null || productLine.getQuantite() <= 0) {
            return false;
        }
        
        // La désignation ne doit pas être un mot-clé de bruit
        String designation = productLine.getDesignation().toUpperCase();
        if (isNoiseLine(designation)) {
            return false;
        }
        
        return true;
    }

    /**
     * Parse une ligne pour extraire les informations d'un produit
     * Amélioré pour extraire les nombres depuis la fin de la ligne
     * Gère les formats: "DIAM 8 HB UNIVERS ACIER    200,000    8,083    1 616,67"
     *                  "CIMENT CPJ 45    23 500,000    1,452    34 125,92"
     */
    private OcrExtractResult.OcrProductLine parseProductLine(String line) {
        OcrExtractResult.OcrProductLine.OcrProductLineBuilder builder = 
            OcrExtractResult.OcrProductLine.builder();

        // Étape 1: Extraire tous les nombres depuis la fin de la ligne
        List<Double> numericValues = extractNumbersFromEnd(line);
        
        if (numericValues.isEmpty()) {
            return null;
        }

        // Étape 2: Extraire la désignation (tout ce qui reste après avoir retiré les nombres)
        String designation = extractDesignation(line, numericValues);
        
        if (designation == null || designation.trim().length() < 3) {
            return null;
        }

        builder.designation(designation.trim());

        // Étape 3: Assigner les valeurs numériques selon leur position et magnitude
        // Les nombres sont extraits depuis la fin, donc l'ordre dans la liste est inversé par rapport à la ligne
        // Format typique d'une facture: [Désignation] [Qté] [Prix unitaire] [Total HT]
        // Après extraction depuis la fin: [Total HT, Prix unitaire, Qté]
        
        if (numericValues.size() >= 3) {
            // Trois valeurs détectées dans l'ordre de la ligne: [Qté, Prix unitaire, Total HT]
            // Format typique: "200,000    8,083    1 616,67"
            Double value1 = numericValues.get(0); // Premier nombre (généralement Qté)
            Double value2 = numericValues.get(1); // Deuxième (généralement Prix unitaire)
            Double value3 = numericValues.get(2); // Troisième (généralement Total HT)
            
            // Validation par magnitude:
            // - La Qté peut être très grande (ex: 23500)
            // - Le Prix unitaire est généralement moyen (ex: 8.08, 1.45, 10.38)
            // - Le Total HT = Qté * Prix unitaire, donc généralement le plus grand
            
            // Si value3 ≈ value1 * value2, alors l'ordre est correct [Qté, Prix, Total]
            double expectedTotal = value1 * value2;
            double diff = Math.abs(value3 - expectedTotal);
            double tolerance = expectedTotal * 0.1; // 10% de tolérance
            
            if (diff <= tolerance) {
                // L'ordre est correct: [Qté, Prix, Total]
                builder.quantite(value1);
                builder.prixUnitaireHT(value2);
                builder.prixTotalHT(value3);
            } else {
                // Essayer d'autres ordres possibles
                // Le plus grand est probablement le Total
                if (value3 >= value1 && value3 >= value2) {
                    // value3 = Total, déterminer Qté et Prix
                    if (value1 > value2 * 100) {
                        // value1 est probablement la Qté (très grand), value2 = Prix
                        builder.quantite(value1);
                        builder.prixUnitaireHT(value2);
                        builder.prixTotalHT(value3);
                    } else {
                        // Par défaut: [Qté, Prix, Total]
                        builder.quantite(value1);
                        builder.prixUnitaireHT(value2);
                        builder.prixTotalHT(value3);
                    }
                } else {
                    // Par défaut: ordre standard [Qté, Prix, Total]
                    builder.quantite(value1);
                    builder.prixUnitaireHT(value2);
                    builder.prixTotalHT(value3);
                }
            }
        } else if (numericValues.size() == 2) {
            // Deux valeurs: Qté et Prix unitaire (ou Qté et Total)
            Double value1 = numericValues.get(0); // Premier
            Double value2 = numericValues.get(1); // Deuxième
            
            // Si value2 est beaucoup plus grand que value1, value2 est probablement le Total HT
            // Sinon, ordre standard [Qté, Prix]
            if (value2 > value1 * 100) {
                // value2 = Total, value1 = Qté (car très grand aussi possible)
                builder.quantite(value1);
                builder.prixTotalHT(value2);
                // Calculer le prix unitaire
                if (value1 > 0) {
                    builder.prixUnitaireHT(value2 / value1);
                }
            } else if (value1 > 100 && value2 < 100) {
                // value1 = Qté (grand), value2 = Prix unitaire (petit)
                builder.quantite(value1);
                builder.prixUnitaireHT(value2);
                builder.prixTotalHT(value1 * value2);
            } else {
                // Par défaut: [Qté, Prix]
                builder.quantite(value1);
                builder.prixUnitaireHT(value2);
                builder.prixTotalHT(value1 * value2);
            }
        } else if (numericValues.size() == 1) {
            // Une seule valeur: probablement la quantité
            builder.quantite(numericValues.get(0));
        }

        // Déterminer l'unité (U par défaut)
        builder.unite("U");

        OcrExtractResult.OcrProductLine result = builder.build();
        
        // Validation finale
        if (result.getQuantite() == null || result.getQuantite() <= 0) {
            return null;
        }

        return result;
    }

    /**
     * Extrait les nombres depuis la fin de la ligne
     * Retourne une liste de nombres trouvés (de droite à gauche dans la ligne)
     * Amélioré pour mieux gérer les formats avec espaces (ex: "1 616,67", "23 500,000")
     */
    private List<Double> extractNumbersFromEnd(String line) {
        List<Double> numbers = new ArrayList<>();
        
        // Pattern amélioré pour détecter les nombres (avec virgule, espaces pour milliers)
        // Exemples: "1 616,67", "23 500,000", "8,083", "200,000"
        // Ne pas matcher les numéros de téléphone (séquences de 2 chiffres)
        Pattern numberPattern = Pattern.compile(
            "\\b\\d{1,3}(?:[\\s]\\d{3})*(?:[,\\.]\\d+)?\\b|" + // Format avec espaces: "1 616,67" ou "23 500,000"
            "\\b\\d+[,\\.]\\d+\\b|" + // Format décimal: "8,083" ou "10.379"
            "(?<!\\d\\s{1,2})\\b\\d{4,}(?:[,\\.]\\d+)?\\b" // Nombres de 4+ chiffres (évite les numéros de téléphone)
        );
        
        Matcher matcher = numberPattern.matcher(line);
        List<Double> allNumbers = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        
        while (matcher.find()) {
            String numberStr = matcher.group();
            Double numValue = parseNumber(numberStr);
            if (numValue != null && numValue > 0) {
                allNumbers.add(numValue);
                positions.add(matcher.start()); // Garder la position pour trier
            }
        }
        
        // Si on a trouvé des nombres, les retourner dans l'ordre (de gauche à droite dans la ligne)
        // Mais on veut les 3 derniers (les plus à droite) qui sont généralement Qté, Prix, Total
        if (allNumbers.size() <= 3) {
            return allNumbers;
        } else {
            // Prendre les 3 derniers (les plus à droite)
            int startIndex = allNumbers.size() - 3;
            return allNumbers.subList(startIndex, allNumbers.size());
        }
    }

    /**
     * Extrait la désignation en retirant les nombres de la ligne
     * Préserve les chiffres qui font partie du nom du produit (ex: "DIAM 8", "CPJ 45")
     */
    private String extractDesignation(String line, List<Double> numericValues) {
        // Approche: trouver le dernier grand nombre (probablement une valeur numérique de colonne)
        // et retirer tout ce qui vient après, puis nettoyer
        
        // D'abord, essayer de trouver où commence la zone numérique (colonnes Qté, Prix, Total)
        // Les valeurs numériques de colonnes sont généralement séparées par plusieurs espaces
        
        // Pattern pour détecter les séparations de colonnes (3+ espaces ou tabs)
        Pattern columnSeparator = Pattern.compile("\\s{3,}|\t+");
        String[] parts = columnSeparator.split(line);
        
        if (parts.length >= 2) {
            // Il y a des colonnes séparées, la première partie est probablement la désignation
            String designation = parts[0].trim();
            // Nettoyer mais garder les chiffres qui sont partie intégrante (comme "DIAM 8")
            designation = designation.replaceAll("\\s+", " ").trim();
            return designation;
        }
        
        // Fallback: retirer les nombres depuis la fin qui correspondent aux valeurs numériques détectées
        String cleaned = line;
        Pattern numberPattern = Pattern.compile("\\b\\d{1,3}(?:[\\s,]\\d{3})*(?:[,\\.]\\d+)?\\b|\\b\\d+[,\\.]\\d+\\b");
        
        // Trouver tous les nombres et retirer ceux qui correspondent aux valeurs détectées
        Matcher matcher = numberPattern.matcher(line);
        List<String> numbersToRemove = new ArrayList<>();
        
        while (matcher.find()) {
            String numberStr = matcher.group();
            Double numValue = parseNumber(numberStr);
            if (numValue != null && numericValues.contains(numValue)) {
                numbersToRemove.add(numberStr);
            }
        }
        
        // Retirer les nombres depuis la fin (garder les chiffres au début qui peuvent être dans le nom)
        for (int i = numbersToRemove.size() - 1; i >= 0; i--) {
            String numberToRemove = numbersToRemove.get(i);
            // Retirer seulement si c'est à la fin de la ligne ou suivi d'espaces
            cleaned = cleaned.replaceFirst("\\s*" + Pattern.quote(numberToRemove) + "\\s*$", "");
            cleaned = cleaned.replaceFirst("\\s{2,}" + Pattern.quote(numberToRemove) + "\\s*", " ");
        }
        
        // Nettoyer: retirer les espaces multiples, caractères spéciaux en fin
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s\\-]+$", ""); // Retirer ponctuation finale
        
        return cleaned;
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

