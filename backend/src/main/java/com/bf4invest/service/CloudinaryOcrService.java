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
     * Gère le cas spécial où les désignations sont groupées séparément des nombres
     */
    private List<OcrExtractResult.OcrProductLine> extractProductLines(String[] lines) {
        log.info("🔎 [OCR] Début extraction produits - {} lignes à analyser", lines.length);
        
        // Logger les 30 premières lignes pour débogage
        int maxPreview = Math.min(30, lines.length);
        for (int i = 0; i < maxPreview; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                log.info("📄 [OCR] Ligne {}: '{}'", i, line);
            }
        }
        
        List<OcrExtractResult.OcrProductLine> productLines = new ArrayList<>();

        // Essayer d'abord l'approche "colonnes séparées" (désignations groupées, puis nombres groupés)
        log.info("🔄 [OCR] Tentative format colonnes séparées...");
        productLines = extractProductLinesSeparatedFormat(lines);
        
        if (!productLines.isEmpty()) {
            log.info("✅ [OCR] {} lignes de produits extraites (format colonnes séparées)", productLines.size());
            return productLines;
        }
        
        // Essayer le format tabulaire (Code + Désignation + Qté + Prix + Total sur mêmes lignes)
        log.info("🔄 [OCR] Tentative format tabulaire...");
        productLines = extractProductLinesTabularFormat(lines);
        
        if (!productLines.isEmpty()) {
            log.info("✅ [OCR] {} lignes de produits extraites (format tabulaire)", productLines.size());
            return productLines;
        }
        
        // Sinon, utiliser l'approche classique (tout sur une ligne ou lignes consécutives)
        log.info("🔄 [OCR] Tentative format classique...");
        productLines = extractProductLinesClassicFormat(lines);
        
        log.info("📦 [OCR] {} lignes de produits extraites (format classique)", productLines.size());
        return productLines;
    }

    /**
     * Extraction pour le format où les désignations sont groupées séparément des nombres
     * Ex: Toutes les désignations après "Désignation", puis tous les nombres après "Qté"
     */
    private List<OcrExtractResult.OcrProductLine> extractProductLinesSeparatedFormat(String[] lines) {
        List<OcrExtractResult.OcrProductLine> productLines = new ArrayList<>();
        
        // Trouver les indices des sections
        int designationStart = -1;
        int qteStart = -1;
        int tableEnd = -1;
        
        log.info("🔍 [OCR] Recherche du format colonnes séparées...");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String lineUpper = line.toUpperCase();
            // Normaliser les accents pour la comparaison
            String lineNormalized = lineUpper
                .replace("É", "E")
                .replace("È", "E")
                .replace("Ê", "E")
                .replace("À", "A");
            
            // Début des désignations
            if (designationStart == -1 && 
                (lineNormalized.contains("DESIGNATION") || lineUpper.contains("DÉSIGNATION"))) {
                designationStart = i + 1;
                log.info("📍 [OCR] Début désignations trouvé ligne {}: '{}'", i, line);
            }
            // Début des quantités/nombres (chercher "Qté", "QTE", "Quantité", etc.)
            else if (qteStart == -1 && designationStart != -1 && 
                     (lineNormalized.equals("QTE") || 
                      lineNormalized.contains("QUANTITE") ||
                      line.equalsIgnoreCase("Qté") ||
                      line.matches("(?i)qt[eéè]?"))) {
                qteStart = i;
                log.info("📍 [OCR] Début nombres trouvé ligne {}: '{}'", i, line);
            }
            // Fin du tableau
            else if (qteStart != -1 && tableEnd == -1 && 
                     ((lineUpper.contains("TOTAL") && (lineUpper.contains("HT") || lineUpper.contains("TTC"))) || 
                      lineUpper.contains("IMPORTANT"))) {
                tableEnd = i;
                log.info("📍 [OCR] Fin tableau trouvée ligne {}: '{}'", i, line);
                break;
            }
        }
        
        log.info("📊 [OCR] Indices détectés - designationStart: {}, qteStart: {}, tableEnd: {}", 
                 designationStart, qteStart, tableEnd);
        
        // Vérifier qu'on a trouvé les deux sections
        if (designationStart == -1 || qteStart == -1 || qteStart <= designationStart) {
            log.info("⚠️ [OCR] Format colonnes séparées non détecté (designationStart={}, qteStart={})", 
                     designationStart, qteStart);
            return productLines;
        }
        
        if (tableEnd == -1) {
            tableEnd = lines.length;
        }
        
        // Collecter les désignations (entre "Désignation" et "Qté")
        List<String> designations = new ArrayList<>();
        for (int i = designationStart; i < qteStart; i++) {
            String line = lines[i].trim();
            if (isValidDesignation(line)) {
                designations.add(line);
                log.info("📝 [OCR] Désignation collectée: '{}'", line);
            } else {
                log.debug("🚫 [OCR] Désignation rejetée: '{}'", line);
            }
        }
        
        // Collecter les nombres (après "Qté", "Prix unitaire", "Montant HT")
        // Sauter les en-têtes de colonnes
        int numbersStart = qteStart;
        for (int i = qteStart; i < Math.min(qteStart + 5, tableEnd); i++) {
            String line = lines[i].trim().toUpperCase();
            if (line.contains("PRIX") || line.contains("MONTANT") || line.equals("HT")) {
                numbersStart = i + 1;
                log.debug("📍 [OCR] Saut d'en-tête ligne {}: '{}'", i, line);
            }
        }
        
        log.info("📍 [OCR] Début des nombres ligne {}", numbersStart);
        
        List<Double> allNumbers = new ArrayList<>();
        for (int i = numbersStart; i < tableEnd; i++) {
            String line = lines[i].trim();
            
            // Ignorer les lignes vides
            if (line.isEmpty()) {
                continue;
            }
            
            // Ignorer les lignes qui sont clairement du texte (pas de nombres significatifs)
            if (line.matches(".*[A-Za-z]{4,}.*") && !line.matches(".*\\d{3,}.*")) {
                log.debug("🚫 [OCR] Ligne texte ignorée: '{}'", line);
                continue;
            }
            
            Double num = parseNumber(line);
            if (num != null && num > 0) {
                allNumbers.add(num);
                log.info("🔢 [OCR] Nombre collecté: {} (ligne: '{}')", num, line);
            }
        }
        
        // Associer désignations et nombres (3 nombres par produit: Qté, Prix, Total)
        int numbersPerProduct = 3;
        int productCount = Math.min(designations.size(), allNumbers.size() / numbersPerProduct);
        
        log.info("📊 [OCR] {} désignations, {} nombres, {} produits attendus", 
                 designations.size(), allNumbers.size(), productCount);
        
        for (int p = 0; p < productCount; p++) {
            String designation = designations.get(p);
            int numIndex = p * numbersPerProduct;
            
            Double qte = allNumbers.get(numIndex);
            Double prix = allNumbers.get(numIndex + 1);
            Double total = allNumbers.get(numIndex + 2);
            
            // Validation: total ≈ qté * prix (avec 10% de tolérance)
            double expectedTotal = qte * prix;
            double diff = Math.abs(total - expectedTotal);
            double tolerance = expectedTotal * 0.1;
            
            if (diff > tolerance && expectedTotal > 0) {
                // Les valeurs ne correspondent pas, essayer de réorganiser
                // Peut-être que le total est en premier?
                if (Math.abs(qte - prix * total) < qte * 0.1) {
                    // Réorganiser: total était en premier
                    Double temp = qte;
                    qte = total;
                    total = temp;
                }
            }
            
            OcrExtractResult.OcrProductLine productLine = OcrExtractResult.OcrProductLine.builder()
                    .designation(designation)
                    .quantite(qte)
                    .prixUnitaireHT(prix)
                    .prixTotalHT(total)
                    .unite("U")
                    .build();
            
            productLines.add(productLine);
            log.info("✅ [OCR] Produit assemblé: {} - Qté: {} - PU: {} - Total: {}", 
                    designation, qte, prix, total);
        }
        
        log.info("📊 [OCR] Format séparé: {} produits extraits", productLines.size());
        return productLines;
    }

    /**
     * Extraction pour le format tabulaire où Code, Désignation, Qté, Prix, Total sont sur la même ligne
     * Ex: "FT12/500 FER TOR/500 DIAM 12 14 147.00 9.50 134 396.50"
     */
    private List<OcrExtractResult.OcrProductLine> extractProductLinesTabularFormat(String[] lines) {
        List<OcrExtractResult.OcrProductLine> productLines = new ArrayList<>();
        
        // Détecter le début du tableau (ligne avec "Code", "Désignations", "Quantité", etc.)
        int tableStart = -1;
        int tableEnd = -1;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toUpperCase();
            String lineNormalized = line.replace("É", "E");
            
            // Chercher un en-tête de tableau avec plusieurs colonnes
            boolean hasCode = line.contains("CODE");
            boolean hasDesignation = lineNormalized.contains("DESIGNATION") || lineNormalized.contains("DESIGNATIONS");
            boolean hasQuantity = lineNormalized.contains("QUANTITE") || lineNormalized.contains("QTE") || line.contains("QTÉ");
            boolean hasPrice = line.contains("PRIX");
            boolean hasAmount = line.contains("MONTANT") || line.contains("TOTAL");
            
            if (tableStart == -1 && hasDesignation && (hasQuantity || hasPrice || hasAmount)) {
                tableStart = i + 1; // Commencer après l'en-tête
                log.info("📍 [OCR] Début tableau tabulaire ligne {}: '{}'", i, lines[i].trim());
            }
            
            // Fin du tableau: ligne avec "TOTAL HT" ou "M.H.T" ou "ARRETEE"
            if (tableStart != -1 && tableEnd == -1 &&
                (line.contains("TOTAL HT") || line.contains("M.H.T") || line.contains("M.T.T.C") ||
                 line.contains("ARRETEE") || line.contains("ARRÊTÉE"))) {
                tableEnd = i;
                log.info("📍 [OCR] Fin tableau tabulaire ligne {}: '{}'", i, lines[i].trim());
                break;
            }
        }
        
        if (tableStart == -1 || tableEnd == -1 || tableEnd <= tableStart) {
            log.info("⚠️ [OCR] Format tabulaire non détecté (tableStart={}, tableEnd={})", tableStart, tableEnd);
            return productLines;
        }
        
        // Parser chaque ligne entre tableStart et tableEnd
        for (int i = tableStart; i < tableEnd; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || isNoiseLine(line)) {
                continue;
            }
            
            // Parser la ligne comme un produit
            OcrExtractResult.OcrProductLine productLine = parseTabularProductLine(line);
            if (productLine != null && isValidProductLine(productLine)) {
                productLines.add(productLine);
                log.info("✅ [OCR] Produit tabulaire: {} - Qté: {} - PU: {} - Total: {}", 
                        productLine.getDesignation(), productLine.getQuantite(), 
                        productLine.getPrixUnitaireHT(), productLine.getPrixTotalHT());
            }
        }
        
        return productLines;
    }
    
    /**
     * Parse une ligne de produit au format tabulaire
     * Format: [Code?] [Désignation] [Qté] [Prix unitaire] [Total]
     */
    private OcrExtractResult.OcrProductLine parseTabularProductLine(String line) {
        // Extraire tous les nombres depuis la fin
        List<Double> numericValues = extractNumbersFromEnd(line);
        
        if (numericValues.size() < 2) {
            return null; // Pas assez de nombres
        }
        
        // Extraire la désignation (tout sauf les nombres à la fin)
        String designation = extractDesignation(line, numericValues);
        if (designation == null || designation.trim().length() < 5) {
            return null;
        }
        
        // Nettoyer la désignation (retirer code produit si présent)
        designation = cleanDesignation(designation.trim());
        
        // Assigner les nombres: normalement les 3 derniers sont [Qté, Prix, Total]
        Double qte = null;
        Double prix = null;
        Double total = null;
        
        if (numericValues.size() >= 3) {
            // Prendre les 3 derniers nombres
            Double val1 = numericValues.get(numericValues.size() - 3);
            Double val2 = numericValues.get(numericValues.size() - 2);
            Double val3 = numericValues.get(numericValues.size() - 1);
            
            // Le total est généralement le plus grand des 3
            // La quantité peut être très grande aussi
            // Le prix unitaire est généralement moyen (entre 5 et 1000)
            
            // Validation: total ≈ qté * prix
            double expected1 = val1 * val2;
            double diff1 = Math.abs(val3 - expected1);
            
            // Essayer l'autre ordre
            double expected2 = val2 * val3;
            double diff2 = Math.abs(val1 - expected2);
            
            if (diff1 < diff2 && diff1 < expected1 * 0.1) {
                // Ordre correct: [Qté, Prix, Total]
                qte = val1;
                prix = val2;
                total = val3;
            } else if (diff2 < diff1 && diff2 < expected2 * 0.1) {
                // Autre ordre: [Prix, Total, Qté]
                prix = val1;
                total = val2;
                qte = val3;
            } else {
                // Par magnitude: le plus grand est le total, le moyen est le prix
                if (val3 >= val1 && val3 >= val2) {
                    total = val3;
                    if (val1 > val2 * 10) {
                        qte = val1;
                        prix = val2;
                    } else {
                        qte = val2;
                        prix = val1;
                    }
                } else {
                    // Par défaut
                    qte = val1;
                    prix = val2;
                    total = val3;
                }
            }
        } else if (numericValues.size() == 2) {
            // Deux nombres: Qté et Prix (ou Qté et Total)
            Double val1 = numericValues.get(0);
            Double val2 = numericValues.get(1);
            
            // Si val2 est beaucoup plus grand, c'est probablement Total
            if (val2 > val1 * 100) {
                qte = val1;
                total = val2;
            } else {
                qte = val1;
                prix = val2;
            }
        } else {
            return null;
        }
        
        // Validation finale: si on a prix et total, vérifier la cohérence
        if (qte != null && prix != null && total != null) {
            double expected = qte * prix;
            double diff = Math.abs(total - expected);
            if (diff > expected * 0.15) {
                // Pas cohérent, peut-être que le prix est TTC
                // Essayer avec TVA incluse (prix * 1.2)
                double expectedTTC = qte * prix * 1.2;
                if (Math.abs(total - expectedTTC) < diff) {
                    // Le prix est TTC, convertir en HT
                    prix = prix / 1.2;
                }
            }
        }
        
        return OcrExtractResult.OcrProductLine.builder()
                .designation(designation)
                .quantite(qte)
                .prixUnitaireHT(prix)
                .prixTotalHT(total)
                .unite("U")
                .build();
    }

    /**
     * Vérifie si une ligne est une désignation valide
     */
    private boolean isValidDesignation(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase();
        
        // Trop court (artefacts OCR comme "AS", "ZA", "CLA", "A S")
        // Retirer les espaces pour le test de longueur
        String withoutSpaces = trimmed.replaceAll("\\s+", "");
        if (withoutSpaces.length() <= 3) {
            return false;
        }
        
        // Doit contenir au moins 3 lettres consécutives
        if (!trimmed.matches(".*[A-Za-z]{3,}.*")) {
            return false;
        }
        
        // FILTRAGE DES LIGNES DE BRUIT - Patterns critiques
        
        // ICE (identifiant fiscal)
        if (upper.contains("ICE") && upper.matches(".*ICE.*\\d{10,}.*")) {
            return false;
        }
        
        // Mode de réglement, Code Client, Référence, etc.
        String[] noisePatterns = {
            "MODE DE", "REGLEMENT", "RÉGLEMENT", "CODE CLIENT", "FACTURE N",
            "BL N°", "DATE:", "REFERENCE", "RÉFÉRENCES", "ECHÉANCE", "ECHEANCE",
            "CONTACT", "E-MAIL", "EMAIL", "ADRESSE", "SIEGE", "QUARTIER",
            "CERTIFIE", "CERTIFICAT", "MONTANT EN LETTRES", "NET HT", "TOTAL TVA",
            "REMISE", "FRAIS", "ARRETEE", "ARRÊTÉE"
        };
        for (String pattern : noisePatterns) {
            if (upper.contains(pattern)) {
                return false;
            }
        }
        
        // Numéros de téléphone (séquences de chiffres avec tirets)
        if (trimmed.matches(".*\\d{10}.*") || // Numéro à 10+ chiffres
            trimmed.matches(".*\\d{4,}-\\d{4,}.*") || // Format avec tirets
            upper.contains("TEL") || upper.contains("FAX")) {
            return false;
        }
        
        // Mots-clés de structure de facture
        String[] structureWords = {"DIVERS", "DATE", "FACTURE", "COMMANDE", "QTE", "PRIX", 
                                   "MONTANT", "CLIENT", "QUANTITE", "TOTAL", "TTC", "HT"};
        for (String word : structureWords) {
            if (upper.equals(word)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Extraction classique: tout sur une ligne ou lignes consécutives
     */
    private List<OcrExtractResult.OcrProductLine> extractProductLinesClassicFormat(String[] lines) {
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
        int i = tableStartIndex;
        while (i < tableEndIndex) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                i++;
                continue;
            }

            // Filtrer les lignes de bruit
            if (isNoiseLine(line)) {
                i++;
                continue;
            }

            // Filtrer les lignes très courtes (artefacts OCR)
            if (line.length() <= 3 && line.matches("^[A-Z]+$")) {
                i++;
                continue;
            }

            // Parser la ligne comme une ligne de produit
            OcrExtractResult.OcrProductLine productLine = parseProductLine(line);
            
            // Si pas de produit détecté, essayer avec la ligne suivante
            if (productLine == null && i + 1 < tableEndIndex) {
                boolean isTextLine = line.matches(".*[A-Za-z]{3,}.*") && 
                                     !line.matches("^[0-9\\s\\.,]+$") &&
                                     line.length() > 5;
                
                if (isTextLine) {
                    for (int lookAhead = 1; lookAhead <= 3 && i + lookAhead < tableEndIndex; lookAhead++) {
                        String nextLine = lines[i + lookAhead].trim();
                        
                        if (nextLine.isEmpty() || (nextLine.length() <= 3 && nextLine.matches("^[A-Z]+$"))) {
                            continue;
                        }
                        
                        boolean hasMultipleNumbers = nextLine.matches(".*\\d+[.,]?\\d*.*\\s+.*\\d+[.,]?\\d*.*");
                        boolean isNumericLine = nextLine.matches(".*\\d{3,}.*") && 
                                               !nextLine.matches(".*[A-Za-z]{5,}.*");
                        
                        if (hasMultipleNumbers && isNumericLine) {
                            String combinedLine = line + "    " + nextLine;
                            productLine = parseProductLine(combinedLine);
                            if (productLine != null && isValidProductLine(productLine)) {
                                i += lookAhead;
                                break;
                            } else {
                                productLine = null;
                            }
                        }
                    }
                }
            }
            
            if (productLine != null && isValidProductLine(productLine)) {
                productLines.add(productLine);
            }
            
            i++;
        }

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
            
            // Ignorer les transactions bancaires (format: "31/12/2025 VIR...")
            if (line.matches("^\\d{2}/\\d{2}/\\d{4}.*VIR.*")) {
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
                    // Vérifier que ce n'est pas une ligne de date ou de transaction
                    String upperLine = line.toUpperCase();
                    if (!upperLine.startsWith("DATE") && !upperLine.contains("VIR") && 
                        !upperLine.matches("^\\d{2}/\\d{2}/\\d{4}.*")) {
                        log.debug("🎯 [OCR] Début tableau détecté (fallback) à la ligne {}: {}", i, line.substring(0, Math.min(50, line.length())));
                        return i;
                    }
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
     * Amélioré pour ne pas s'arrêter trop tôt
     */
    private boolean isTableEnd(String line) {
        String upperLine = line.toUpperCase();
        
        // Fin claire du tableau: "TOTAL HT" ou "TOTAL TTC" (pas seulement "TOTAL" seul)
        if (upperLine.contains("TOTAL") && (upperLine.contains("HT") || upperLine.contains("TTC"))) {
            return true;
        }
        
        // T.V.A ou TVA (mais pas dans une ligne de produit)
        if ((upperLine.contains("T.V.A") || upperLine.contains("TVA")) && 
            upperLine.matches(".*T\\.?V\\.?A.*\\d+.*")) { // Doit contenir un pourcentage ou montant
            return true;
        }
        
        // Sous-total
        if (upperLine.contains("SOUS-TOTAL") || upperLine.contains("SOUSTOTAL")) {
            return true;
        }
        
        // "ARRÊTER" ou "ARRETE" (dans le contexte d'une facture, c'est généralement la fin)
        if (upperLine.contains("ARRÊTER") || upperLine.contains("ARRETE")) {
            return true;
        }
        
        // "MODE DE REGLEMENT" (mode de paiement, généralement après le tableau)
        if (upperLine.contains("MODE DE REGLEMENT")) {
            return true;
        }
        
        // Ne pas s'arrêter sur "IMPORTANT" ou "CONFORMEMENT" car c'est généralement après les totaux
        // Ces lignes ne marquent pas la fin du tableau de produits
        
        return false;
    }

    /**
     * Vérifie si une ligne est du bruit (adresse, téléphone, etc.) et doit être ignorée
     */
    private boolean isNoiseLine(String line) {
        String trimmed = line.trim();
        String upperLine = trimmed.toUpperCase();
        
        // Ignorer les lignes trop courtes (< 5 caractères)
        if (trimmed.length() < 5) {
            return true;
        }
        
        // Ignorer les transactions bancaires (format: "31/12/2025 VIR EXP...")
        if (trimmed.matches("^\\d{2}/\\d{2}/\\d{4}.*VIR.*")) {
            return true;
        }
        
        // Ignorer les lignes qui sont uniquement des nombres ou des symboles
        if (trimmed.matches("^[0-9\\s\\.,\\-\\+/]+$")) {
            return true;
        }
        
        // Mots-clés à ignorer (métadonnées de facture)
        String[] noiseKeywords = {
            // Contacts
            "TEL", "FAX", "E-MAIL", "EMAIL", "CONTACT", "@",
            // Identifiants
            "ICE", "IF ", "R.C", "RC ", "CNSS", "PATENTE",
            // Adresses
            "ADRESSE", "SIEGE", "SIÉGE", "QUARTIER", "RUE ", "ROUTE",
            "Z.INDUSTRIELLE", "ZONE INDUSTRIELLE", "DEPOT",
            "SELOUANE", "NADOR", "MEKNES", "MEKNÈS",
            // Structure facture
            "RAISON SOCIALE", "CODE CLIENT", "MODE DE", "REGLEMENT", "RÉGLEMENT",
            "BL N°", "BL N", "FACTURE N", "REFERENCE", "RÉFÉRENCES", "ECHÉANCE", "ECHEANCE",
            // Totaux et mentions légales
            "TOTAL HT", "TOTAL TTC", "NET HT", "T.V.A", "TVA ", "REMISE",
            "DAHIR", "LOI", "PENALITE", "PENALITÉ",
            "IMPORTANT", "CONFORMEMENT", "DISPOSITIONS",
            "ARRETEE", "ARRÊTÉE", "SOMME", "DIRHAMS", "DHS",
            // Certifications
            "CERTIFIE", "CERTIFICAT", "BETON PRET",
            // Signatures
            "RECEPTION", "SIGNATURE", "MONTANT EN LETTRES",
            // Noms d'entreprises (pas de produits)
            "GUARIMETAL", "SORIMAC", "WESTMAT", "BF4 INVEST",
            "SARL", "STE ", "S.A.R.L",
            // Autres bruits
            "COPIE", "COPL", "VILLE", "B.P", "BP ",
            "CAPITALE", "VENTE MATERIAUX", "CONSTRUCTION"
        };
        
        for (String keyword : noiseKeywords) {
            if (upperLine.contains(keyword)) {
                return true;
            }
        }
        
        // Pattern pour les lignes qui commencent par "Tél:" ou "Fax:" ou "Contact"
        if (upperLine.startsWith("TEL") || upperLine.startsWith("FAX") || 
            upperLine.startsWith("CONTACT") || upperLine.contains("TEL:") || 
            upperLine.contains("FAX:")) {
            return true;
        }
        
        // Pattern pour les numéros de téléphone (séquence 05 XX XX XX XX)
        if (trimmed.matches(".*0[5-7]\\s*\\d{2}\\s*\\d{2}\\s*\\d{2}\\s*\\d{2}.*")) {
            return true;
        }
        
        // Pattern pour numéros avec tirets (0536334951-0536609733)
        if (trimmed.matches(".*\\d{10}-\\d{10}.*")) {
            return true;
        }
        
        // Pattern pour les lignes qui contiennent un slash avec des nombres de chaque côté
        // (typique des numéros de téléphone multiples: "60/05")
        if (trimmed.matches(".*\\d{8,}.*/.*\\d{8,}.*")) {
            return true;
        }
        
        // Lignes contenant ICE suivi de chiffres (identifiant fiscal)
        if (trimmed.matches(".*ICE[:\\s]*\\d{10,}.*")) {
            return true;
        }
        
        // Lignes qui ressemblent à des codes sans désignation (ex: "FT12/500" seul)
        if (trimmed.matches("^[A-Z]{1,5}\\d{1,3}/\\d{1,4}$")) {
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
        
        String designation = productLine.getDesignation();
        
        // Désignation doit avoir au moins 3 caractères
        if (designation == null || designation.trim().length() < 3) {
            log.debug("🚫 [OCR] Produit rejeté - désignation trop courte: '{}'", designation);
            return false;
        }
        
        // Doit avoir au moins une quantité > 0
        if (productLine.getQuantite() == null || productLine.getQuantite() <= 0) {
            log.debug("🚫 [OCR] Produit rejeté - quantité invalide: {} pour '{}'", productLine.getQuantite(), designation);
            return false;
        }
        
        String designationUpper = designation.toUpperCase();
        
        // La désignation ne doit pas être un mot-clé de bruit
        if (isNoiseLine(designation)) {
            log.debug("🚫 [OCR] Produit rejeté - c'est une ligne de bruit: '{}'", designation);
            return false;
        }
        
        // Rejeter les lignes qui contiennent des métadonnées de document
        String[] metadataPatterns = {
            "BL N°", "DATE:", "ICE:", "MODE DE", "RÉGLEMENT", "REGLEMENT",
            "CODE CLIENT", "FACTURE N", "CONTACT", "E-MAIL", "EMAIL",
            "CERTIFIE", "NET HT", "TOTAL", "REMISE", "DESIGNATIONS"
        };
        for (String pattern : metadataPatterns) {
            if (designationUpper.contains(pattern)) {
                log.debug("🚫 [OCR] Produit rejeté - contient métadonnée '{}': '{}'", pattern, designation);
                return false;
            }
        }
        
        // Rejeter les transactions bancaires (format: "31/12/2025 VIR EXP...")
        if (designation.matches("^\\d{2}/\\d{2}/\\d{4}.*VIR.*")) {
            log.debug("🚫 [OCR] Produit rejeté - transaction bancaire: '{}'", designation);
            return false;
        }
        
        // Rejeter les dates seules ou dates avec texte court
        if (designation.matches("^\\d{2}/\\d{2}/\\d{4}.*") && designation.length() < 30) {
            log.debug("🚫 [OCR] Produit rejeté - ligne de date: '{}'", designation);
            return false;
        }
        
        // Rejeter si la désignation contient un email
        if (designation.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*")) {
            log.debug("🚫 [OCR] Produit rejeté - contient email: '{}'", designation);
            return false;
        }
        
        // Rejeter les codes de référence seuls (ex: "F01054/25")
        if (designation.matches("^[A-Z0-9]{1,10}/\\d{1,4}$")) {
            log.debug("🚫 [OCR] Produit rejeté - code référence seul: '{}'", designation);
            return false;
        }
        
        // Rejeter si la quantité est trop grande (probablement un numéro de téléphone mal parsé)
        // Ex: 536609733 est un numéro de téléphone, pas une quantité
        if (productLine.getQuantite() > 1000000) {
            log.debug("🚫 [OCR] Produit rejeté - quantité suspecte (trop grande): {} pour '{}'", productLine.getQuantite(), designation);
            return false;
        }
        
        // Le prix unitaire doit être raisonnable (pas des millions)
        if (productLine.getPrixUnitaireHT() != null && productLine.getPrixUnitaireHT() > 100000) {
            log.debug("🚫 [OCR] Produit rejeté - prix unitaire suspect: {} pour '{}'", productLine.getPrixUnitaireHT(), designation);
            return false;
        }
        
        log.debug("✅ [OCR] Produit validé: {} - Qté: {} - PU: {} - Total: {}", 
                  designation, productLine.getQuantite(), productLine.getPrixUnitaireHT(), productLine.getPrixTotalHT());
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
     * Gère le cas où la désignation est sur une ligne et les nombres sur une autre
     */
    private String extractDesignation(String line, List<Double> numericValues) {
        // Si la ligne ne contient que du texte (pas de nombres), c'est probablement juste la désignation
        Pattern numberPattern = Pattern.compile("\\b\\d{4,}[.,]?\\d*\\b"); // Nombres de 4+ chiffres (pas les petits chiffres dans le nom)
        if (!numberPattern.matcher(line).find()) {
            // Pas de grands nombres, nettoyer et retourner
            String cleaned = cleanDesignation(line.trim());
            return cleaned;
        }
        
        // Approche: trouver où commence la zone numérique (colonnes Qté, Prix, Total)
        // Les valeurs numériques de colonnes sont généralement séparées par plusieurs espaces
        
        // Pattern pour détecter les séparations de colonnes (3+ espaces ou tabs)
        Pattern columnSeparator = Pattern.compile("\\s{3,}|\t+");
        String[] parts = columnSeparator.split(line);
        
        if (parts.length >= 2) {
            // Il y a des colonnes séparées, la première partie est probablement Code + Désignation
            String designation = parts[0].trim();
            
            // Si la première partie a plusieurs colonnes (Code + Désignation), les joindre
            // Ex: "FT12/500" "FER TOR/500 DIAM 12" -> "FER TOR/500 DIAM 12"
            if (parts.length >= 3) {
                // Vérifier si parts[0] ressemble à un code produit (ex: FT12/500, B30G)
                if (parts[0].matches("^[A-Z0-9]{1,10}(/\\d+)?$") && parts[1].matches(".*[A-Za-z]{3,}.*")) {
                    // Le premier élément est un code, prendre le deuxième comme désignation
                    designation = parts[1].trim();
                }
            }
            
            // Nettoyer et retourner
            designation = cleanDesignation(designation);
            return designation;
        }
        
        // Fallback: retirer les grands nombres depuis la fin qui correspondent aux valeurs numériques détectées
        String cleaned = line;
        Pattern bigNumberPattern = Pattern.compile("\\b\\d{1,3}(?:[\\s,]\\d{3})*(?:[,\\.]\\d+)?\\b|\\b\\d+[,\\.]\\d+\\b");
        
        // Trouver tous les grands nombres et retirer ceux qui correspondent aux valeurs détectées
        Matcher matcher = bigNumberPattern.matcher(line);
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
        
        return cleanDesignation(cleaned);
    }
    
    /**
     * Nettoie une désignation:
     * - Retire les codes produits du début (ex: "FT12/500", "B30G")
     * - Retire les éléments de bruit
     */
    private String cleanDesignation(String designation) {
        if (designation == null || designation.trim().isEmpty()) {
            return designation;
        }
        
        String cleaned = designation.trim();
        
        // Pattern pour les codes produits au début: FT12/500, B30G, ADJUVANT (si suivi d'un texte)
        // Code produit: lettres+chiffres sans espaces, optionnellement suivi de /nombre
        Pattern codeProductPattern = Pattern.compile("^([A-Z0-9]{2,10}(/\\d+)?)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = codeProductPattern.matcher(cleaned);
        
        if (matcher.matches()) {
            String potentialCode = matcher.group(1);
            String restOfLine = matcher.group(3);
            
            // Vérifier si potentialCode ressemble vraiment à un code (pas une désignation)
            // Un code est généralement court, avec des chiffres, et sans espace
            // Une désignation contient des mots (plusieurs lettres consécutives)
            boolean isCode = potentialCode.matches("^[A-Z]{1,4}\\d+(/\\d+)?$") || // FT12/500
                             potentialCode.matches("^[A-Z0-9]{2,6}$"); // B30G
            
            // Mais si restOfLine est très court ou ne ressemble pas à une désignation, garder tout
            boolean restIsDesignation = restOfLine.length() > 5 && 
                                        restOfLine.matches(".*[A-Za-z]{3,}.*");
            
            if (isCode && restIsDesignation) {
                cleaned = restOfLine;
                log.debug("🔄 [OCR] Code produit retiré: '{}' -> '{}'", potentialCode, cleaned);
            }
        }
        
        // Nettoyer les espaces multiples
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
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


