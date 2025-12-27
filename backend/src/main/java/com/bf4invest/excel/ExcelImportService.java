package com.bf4invest.excel;

import com.bf4invest.dto.ImportResult;
import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.DateFormatConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {
    
    private final BandeCommandeRepository bcRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_YYYYMMDD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final NumberFormat FRENCH_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    
    public ImportResult importExcel(MultipartFile file) {
        ImportResult result = new ImportResult();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.getErrors().add("Le fichier Excel est vide ou ne contient que l'en-tête");
                return result;
            }
            
            // Mapping intelligent des colonnes
            Map<String, Integer> columnMap = mapColumnsIntelligent(sheet.getRow(0));
            log.info("Mapped columns: {}", columnMap);
            
            result.setTotalRows(sheet.getLastRowNum());
            
            // Maps pour grouper par clé
            Map<String, BandeCommande> bcMap = new LinkedHashMap<>(); // LinkedHashMap pour préserver l'ordre
            Map<String, FactureAchat> faMap = new LinkedHashMap<>();
            Map<String, FactureVente> fvMap = new LinkedHashMap<>();
            
            // Map pour grouper les lignes par facture
            Map<String, List<LineItem>> faLignesMap = new HashMap<>();
            Map<String, List<LineItem>> fvLignesMap = new HashMap<>();
            
            // Maps temporaires pour associer factures aux BCs
            Map<String, String> faToBcNumMap = new HashMap<>(); // numeroFA -> numeroBC
            Map<String, String> fvToBcNumMap = new HashMap<>(); // numeroFV -> numeroBC
            
            int processedRows = 0;
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;
                
                try {
                    processRow(row, columnMap, bcMap, faMap, fvMap, faLignesMap, fvLignesMap, 
                              faToBcNumMap, fvToBcNumMap, result);
                    processedRows++;
                } catch (Exception e) {
                    log.error("Error processing row {}: {}", i + 1, e.getMessage(), e);
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, e.getMessage()));
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
            
            // Calculer les totaux pour les BC
            for (BandeCommande bc : bcMap.values()) {
                calculateBCTotals(bc);
            }
            
            // Calculer les totaux et ajouter les lignes pour les factures
            for (FactureAchat fa : faMap.values()) {
                List<LineItem> lignes = faLignesMap.getOrDefault(fa.getNumeroFactureAchat(), new ArrayList<>());
                fa.setLignes(lignes);
                calculateFactureAchatTotals(fa);
            }
            
            for (FactureVente fv : fvMap.values()) {
                List<LineItem> lignes = fvLignesMap.getOrDefault(fv.getNumeroFactureVente(), new ArrayList<>());
                fv.setLignes(lignes);
                calculateFactureVenteTotals(fv);
            }
            
            // Sauvegarder les BC d'abord pour obtenir leurs IDs
            Map<String, String> bcNumToIdMap = new HashMap<>();
            for (BandeCommande bc : bcMap.values()) {
                try {
                    bc.setCreatedAt(LocalDateTime.now());
                    bc.setUpdatedAt(LocalDateTime.now());
                    
                    // Vérifier si BC existe déjà
                    Optional<BandeCommande> existing = bcRepository.findByNumeroBC(bc.getNumeroBC());
                    String bcId;
                    if (existing.isPresent()) {
                        // Mettre à jour la BC existante
                        BandeCommande existingBC = existing.get();
                        existingBC.setLignes(bc.getLignes());
                        existingBC.setDateBC(bc.getDateBC());
                        existingBC.setClientId(bc.getClientId());
                        existingBC.setFournisseurId(bc.getFournisseurId());
                        existingBC.setEtat("envoyee");
                        existingBC.setUpdatedAt(LocalDateTime.now());
                        calculateBCTotals(existingBC);
                        BandeCommande saved = bcRepository.save(existingBC);
                        bcId = saved.getId();
                        result.getWarnings().add("BC " + bc.getNumeroBC() + " mise à jour");
                    } else {
                        BandeCommande saved = bcRepository.save(bc);
                        bcId = saved.getId();
                    }
                    
                    bcNumToIdMap.put(bc.getNumeroBC(), bcId);
                    bc.setId(bcId); // Mettre à jour l'ID dans la map
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    log.error("Error saving BC {}", bc.getNumeroBC(), e);
                    result.getErrors().add("Erreur sauvegarde BC " + bc.getNumeroBC() + ": " + e.getMessage());
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
            
            // Sauvegarder les factures achat
            for (FactureAchat fa : faMap.values()) {
                try {
                    fa.setCreatedAt(LocalDateTime.now());
                    fa.setUpdatedAt(LocalDateTime.now());
                    
                    // Lier à la BC en utilisant la map temporaire
                    String bcNum = faToBcNumMap.get(fa.getNumeroFactureAchat());
                    if (bcNum != null && bcNumToIdMap.containsKey(bcNum)) {
                        fa.setBandeCommandeId(bcNumToIdMap.get(bcNum));
                    } else {
                        // Fallback: chercher par fournisseur et date
                        for (BandeCommande bc : bcMap.values()) {
                            if (bc.getFournisseurId() != null && bc.getFournisseurId().equals(fa.getFournisseurId()) 
                                && bc.getDateBC() != null && fa.getDateFacture() != null
                                && bc.getDateBC().equals(fa.getDateFacture())) {
                                fa.setBandeCommandeId(bc.getId());
                                break;
                            }
                        }
                    }
                    
                    // Vérifier doublon
                    Optional<FactureAchat> existing = factureAchatRepository.findByNumeroFactureAchat(fa.getNumeroFactureAchat());
                    if (existing.isPresent()) {
                        result.getWarnings().add("Facture Achat " + fa.getNumeroFactureAchat() + " déjà existante, ignorée");
                        continue;
                    }
                    
                    factureAchatRepository.save(fa);
                } catch (Exception e) {
                    log.error("Error saving FA {}", fa.getNumeroFactureAchat(), e);
                    result.getErrors().add("Erreur sauvegarde FA " + fa.getNumeroFactureAchat() + ": " + e.getMessage());
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
            
            // Sauvegarder les factures vente
            for (FactureVente fv : fvMap.values()) {
                try {
                    fv.setCreatedAt(LocalDateTime.now());
                    fv.setUpdatedAt(LocalDateTime.now());
                    
                    // Lier à la BC en utilisant la map temporaire
                    String bcNum = fvToBcNumMap.get(fv.getNumeroFactureVente());
                    if (bcNum != null && bcNumToIdMap.containsKey(bcNum)) {
                        fv.setBandeCommandeId(bcNumToIdMap.get(bcNum));
                    } else {
                        // Fallback: chercher par client et date
                        for (BandeCommande bc : bcMap.values()) {
                            if (bc.getClientId() != null && bc.getClientId().equals(fv.getClientId())
                                && bc.getDateBC() != null && fv.getDateFacture() != null
                                && bc.getDateBC().equals(fv.getDateFacture())) {
                                fv.setBandeCommandeId(bc.getId());
                                break;
                            }
                        }
                    }
                    
                    // Vérifier doublon
                    Optional<FactureVente> existing = factureVenteRepository.findByNumeroFactureVente(fv.getNumeroFactureVente());
                    if (existing.isPresent()) {
                        result.getWarnings().add("Facture Vente " + fv.getNumeroFactureVente() + " déjà existante, ignorée");
                        continue;
                    }
                    
                    factureVenteRepository.save(fv);
                } catch (Exception e) {
                    log.error("Error saving FV {}", fv.getNumeroFactureVente(), e);
                    result.getErrors().add("Erreur sauvegarde FV " + fv.getNumeroFactureVente() + ": " + e.getMessage());
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
            
            log.info("Import completed: {} rows processed, {} BCs, {} FAs, {} FVs", 
                    processedRows, bcMap.size(), faMap.size(), fvMap.size());
            
        } catch (Exception e) {
            log.error("Error importing Excel file", e);
            result.getErrors().add("Erreur lecture fichier: " + e.getMessage());
            result.setErrorCount(result.getErrorCount() + 1);
        }
        
        return result;
    }
    
    /**
     * Mapping intelligent des colonnes avec plusieurs variantes possibles
     */
    private Map<String, Integer> mapColumnsIntelligent(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        Map<String, List<String>> columnAliases = getColumnAliases();
        Set<Integer> usedColumns = new HashSet<>(); // Pour éviter les doublons
        
        // Première passe: mapper les colonnes les plus spécifiques d'abord
        // Liste des clés dans l'ordre de priorité (plus spécifique en premier)
        List<String> priorityOrder = Arrays.asList(
            "numero_facture_fournisseur", // N FAC FRS avant FRS
            "numero_facture_vente",       // N FAC VTE avant autres
            "date_facture_achat",
            "date_facture_vente",
            "numero_bc",
            "numero_article",
            "quantite_bc",
            "quantite_livree",
            "prix_achat_unitaire_ht",
            "prix_achat_total_ht",
            "prix_achat_unitaire_ttc",
            "prix_vente_unitaire_ht",
            "prix_vente_unitaire_ttc",
            "facture_achat_ttc",
            "facture_vente_ttc",
            "taux_tva",
            "marge_unitaire_ttc",
            "date_bc",
            "ice",
            "fournisseur",
            "client",
            "designation",
            "unite"
        );
        
        for (Cell cell : headerRow) {
            if (cell == null) continue;
            
            int colIndex = cell.getColumnIndex();
            if (usedColumns.contains(colIndex)) continue;
            
            String cellValue = getCellStringValue(cell).toLowerCase().trim();
            if (cellValue.isEmpty()) continue;
            
            // Normaliser: retirer accents, espaces multiples, caractères spéciaux
            String normalized = normalizeColumnName(cellValue);
            
            // Chercher dans l'ordre de priorité
            boolean matched = false;
            for (String priorityKey : priorityOrder) {
                if (matched) break;
                
                List<String> aliases = columnAliases.get(priorityKey);
                if (aliases == null) continue;
                
                // Ne pas mapper si déjà mappé
                if (map.containsKey(priorityKey)) continue;
                
                for (String alias : aliases) {
                    String aliasNormalized = normalizeColumnName(alias.toLowerCase());
                    
                    // Match exact (priorité) ou contient
                    if (normalized.equals(aliasNormalized)) {
                        map.put(priorityKey, colIndex);
                        usedColumns.add(colIndex);
                        log.info("Mapped column '{}' (normalized: '{}') to '{}' (exact match)", cellValue, normalized, priorityKey);
                        matched = true;
                        break;
                    }
                }
            }
            
            // Deuxième passe: match partiel pour les colonnes non encore mappées
            if (!matched) {
                for (Map.Entry<String, List<String>> entry : columnAliases.entrySet()) {
                    if (matched) break;
                    if (map.containsKey(entry.getKey())) continue; // Déjà mappé
                    if (usedColumns.contains(colIndex)) break; // Colonne déjà utilisée
                    
                    for (String alias : entry.getValue()) {
                        String aliasNormalized = normalizeColumnName(alias.toLowerCase());
                        
                        if (normalized.contains(aliasNormalized) || aliasNormalized.contains(normalized)) {
                            map.put(entry.getKey(), colIndex);
                            usedColumns.add(colIndex);
                            log.info("Mapped column '{}' (normalized: '{}') to '{}' (partial match)", cellValue, normalized, entry.getKey());
                            matched = true;
                            break;
                        }
                    }
                }
            }
        }
        
        log.info("Final column mapping: {}", map);
        return map;
    }
    
    /**
     * Définit tous les aliases possibles pour chaque colonne
     */
    private Map<String, List<String>> getColumnAliases() {
        Map<String, List<String>> aliases = new HashMap<>();
        
        // DATE BC
        aliases.put("date_bc", Arrays.asList("date bc", "datebc", "date", "dat bc"));
        
        // N° FAC VTE
        aliases.put("numero_facture_vente", Arrays.asList("n° fac vte", "n fac vte", "numero fac vente", 
                "numero facture vente", "n° facture vente", "facture vente", "fac vte", "fv"));
        
        // DATE FAC VTE
        aliases.put("date_facture_vente", Arrays.asList("date fac vte", "datefac vte", "date facture vente"));
        
        // ICE
        aliases.put("ice", Arrays.asList("ice", "i.c.e"));
        
        // N FAC FRS (DOIT être avant "fournisseur" pour éviter les conflits)
        aliases.put("numero_facture_fournisseur", Arrays.asList("n fac frs", "n fac fr", "n° fac frs", 
                "numero fac fournisseur", "numero facture fournisseur", "facture fournisseur", "fac frs",
                "n° fac fr", "n fac fournisseur", "numero fac frs"));
        
        // FRS (fournisseur - doit être après numero_facture_fournisseur)
        aliases.put("fournisseur", Arrays.asList("frs", "fr", "fournisseur", "fourni", "supplier", "frs "));
        
        // CLENT
        aliases.put("client", Arrays.asList("clent", "client", "clt"));
        
        // N° BC
        aliases.put("numero_bc", Arrays.asList("n° bc", "n bc", "numero bc", "numero_bc", "bc", "num bc"));
        
        // N° ARTICLE
        aliases.put("numero_article", Arrays.asList("n° article", "n article", "numero article", 
                "numero_article", "article", "n° artic", "n artic"));
        
        // DESIGNATION
        aliases.put("designation", Arrays.asList("designation", "design", "desc", "description", "produit"));
        
        // U
        aliases.put("unite", Arrays.asList("u", "unite", "unit", "unité"));
        
        // QT BC
        aliases.put("quantite_bc", Arrays.asList("qt bc", "qtbc", "quantite bc", "quantite_achetee", 
                "quantité achetée", "qte achat", "quantite achat"));
        
        // PRIX ACHAT U HT
        aliases.put("prix_achat_unitaire_ht", Arrays.asList("prix achat u ht", "prix achat unitaire ht", 
                "prix achat unit ht", "pau ht", "pax ht"));
        
        // PRIX ACHAT T HT
        aliases.put("prix_achat_total_ht", Arrays.asList("prix achat t ht", "prix achat total ht", 
                "total achat ht", "tah ht"));
        
        // TX TVA
        aliases.put("taux_tva", Arrays.asList("tx tva", "taux tva", "tva", "taxe", "tva %"));
        
        // FACTURE ACHAT TTC
        aliases.put("facture_achat_ttc", Arrays.asList("facture achat ttc", "facture achat ttc ", 
                "fa ttc", "total achat ttc"));
        
        // PRIX ACHAT U TTC
        aliases.put("prix_achat_unitaire_ttc", Arrays.asList("prix achat u ttc", "prix achat unitaire ttc", 
                "pau ttc"));
        
        // PRIX DE VENTE U TTC
        aliases.put("prix_vente_unitaire_ttc", Arrays.asList("prix de vente u tt", "prix de vente u ttc", 
                "prix vente unitaire ttc", "pv u ttc", "prix vente u ttc"));
        
        // MARGE U TTC
        aliases.put("marge_unitaire_ttc", Arrays.asList("marge u ttc", "marge unitaire ttc", "marge", "marge ut"));
        
        // QT LIVREE
        aliases.put("quantite_livree", Arrays.asList("qt livree", "qt livref", "quantite livree", 
                "quantite_vendue", "quantité vendue", "qte vente", "quantite vente"));
        
        // PRIX DE VENTE U HT
        aliases.put("prix_vente_unitaire_ht", Arrays.asList("prix de vente u ht", "prix vente unitaire ht", 
                "pv u ht", "prix vente u ht"));
        
        // FACTURE VENTE TTC
        aliases.put("facture_vente_ttc", Arrays.asList("facture vente ttc", "facture vente ttc ", 
                "fv ttc", "total vente ttc"));
        
        return aliases;
    }
    
    private String normalizeColumnName(String name) {
        if (name == null) return "";
        
        return name
                .toLowerCase()
                .replaceAll("[°'`]", "")  // Retirer caractères spéciaux
                .replaceAll("[éèêë]", "e")  // Normaliser accents e
                .replaceAll("[àâä]", "a")   // Normaliser accents a
                .replaceAll("[îï]", "i")    // Normaliser accents i
                .replaceAll("[ôö]", "o")    // Normaliser accents o
                .replaceAll("[ùûü]", "u")   // Normaliser accents u
                .replaceAll("ç", "c")       // Normaliser ç
                .replaceAll("[^a-z0-9\\s]", "")  // Retirer autres caractères spéciaux
                .replaceAll("\\s+", " ")    // Espaces multiples -> un seul espace
                .trim();
    }
    
    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null) {
                String value = getCellStringValue(cell).trim();
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void processRow(Row row, Map<String, Integer> columnMap,
                           Map<String, BandeCommande> bcMap,
                           Map<String, FactureAchat> faMap,
                           Map<String, FactureVente> fvMap,
                           Map<String, List<LineItem>> faLignesMap,
                           Map<String, List<LineItem>> fvLignesMap,
                           Map<String, String> faToBcNumMap, // Map temporaire: numeroFA -> numeroBC
                           Map<String, String> fvToBcNumMap, // Map temporaire: numeroFV -> numeroBC
                           ImportResult result) {
        
        // 1. Récupérer les données de base
        String numeroBC = getCellValue(row, columnMap, "numero_bc");
        if (numeroBC == null || numeroBC.trim().isEmpty()) {
            throw new RuntimeException("Numéro BC manquant");
        }
        numeroBC = numeroBC.trim();
        final String finalNumeroBC = numeroBC; // Copie finale pour lambda
        
        // 2. Créer ou récupérer le client
        String clientIce = getCellValue(row, columnMap, "ice");
        String clientNom = getCellValue(row, columnMap, "client");
        String clientId = findOrCreateClient(clientIce, clientNom, result);
        final String finalClientId = clientId; // Copie finale pour lambda
        
        // 3. Créer ou récupérer le fournisseur
        String fournisseurNom = getCellValue(row, columnMap, "fournisseur");
        String fournisseurId = findOrCreateFournisseur(fournisseurNom, result);
        final String finalFournisseurId = fournisseurId; // Copie finale pour lambda
        
        // 4. Créer ou récupérer la BC
        BandeCommande bc = bcMap.computeIfAbsent(finalNumeroBC, k -> {
            BandeCommande newBc = new BandeCommande();
            newBc.setNumeroBC(finalNumeroBC);
            
            // Parser la date BC directement depuis la cellule Excel
            Integer dateBCCol = columnMap.get("date_bc");
            LocalDate dateBC = null;
            if (dateBCCol != null) {
                Cell dateCell = row.getCell(dateBCCol);
                dateBC = parseDateFromCell(dateCell);
                if (dateBC == null) {
                    // Fallback sur parseDate si parseDateFromCell échoue
                    dateBC = parseDate(getCellValue(row, columnMap, "date_bc"));
                }
            }
            newBc.setDateBC(dateBC);
            
            newBc.setClientId(finalClientId);
            newBc.setFournisseurId(finalFournisseurId);
            newBc.setEtat("envoyee"); // Si factures présentes, la BC est déjà envoyée
            newBc.setLignes(new ArrayList<>());
            return newBc;
        });
        
        // 5. Créer la ligne produit
        LineItem ligne = createLineItem(row, columnMap, result);
        bc.getLignes().add(ligne);
        
        // 6. Créer ou récupérer la facture achat (N FAC FRS)
        String numeroFA = getCellValue(row, columnMap, "numero_facture_fournisseur");
        if (numeroFA != null && !numeroFA.trim().isEmpty()) {
            numeroFA = numeroFA.trim();
            final String finalNumeroFA = numeroFA; // Copie finale pour lambda
            
            FactureAchat fa = faMap.computeIfAbsent(finalNumeroFA, k -> {
                FactureAchat newFa = new FactureAchat();
                newFa.setNumeroFactureAchat(finalNumeroFA);
                
                // Date facture achat (utiliser date BC si pas de date spécifique dans Excel)
                LocalDate dateFacture = null;
                Integer dateFACol = columnMap.get("date_facture_achat");
                if (dateFACol != null) {
                    Cell dateCell = row.getCell(dateFACol);
                    dateFacture = parseDateFromCell(dateCell);
                }
                
                // Fallback sur date BC si pas de date facture
                if (dateFacture == null) {
                    Integer dateBCCol = columnMap.get("date_bc");
                    if (dateBCCol != null) {
                        Cell dateCell = row.getCell(dateBCCol);
                        dateFacture = parseDateFromCell(dateCell);
                    }
                    if (dateFacture == null) {
                        String dateFA = getCellValue(row, columnMap, "date_bc");
                        dateFacture = parseDate(dateFA);
                    }
                }
                newFa.setDateFacture(dateFacture);
                
                // Date échéance = date facture + 2 mois (règle métier)
                if (dateFacture != null) {
                    newFa.setDateEcheance(dateFacture.plusMonths(2));
                }
                
                newFa.setFournisseurId(finalFournisseurId);
                newFa.setEtatPaiement("non_regle");
                newFa.setLignes(new ArrayList<>());
                newFa.setPaiements(new ArrayList<>());
                
                return newFa;
            });
            
            // Stocker l'association FA -> BC
            faToBcNumMap.put(finalNumeroFA, finalNumeroBC);
            
            // Ajouter la ligne à la facture achat
            LineItem faLigne = createLineItemForFacture(row, columnMap, result);
            faLignesMap.computeIfAbsent(finalNumeroFA, k -> new ArrayList<>()).add(faLigne);
        }
        
        // 7. Créer ou récupérer la facture vente
        String numeroFV = getCellValue(row, columnMap, "numero_facture_vente");
        if (numeroFV != null && !numeroFV.trim().isEmpty()) {
            numeroFV = numeroFV.trim();
            final String finalNumeroFV = numeroFV; // Copie finale pour lambda
            FactureVente fv = fvMap.computeIfAbsent(finalNumeroFV, k -> {
                FactureVente newFv = new FactureVente();
                newFv.setNumeroFactureVente(finalNumeroFV);
                
                // Date facture vente
                LocalDate dateFacture = null;
                Integer dateFVCol = columnMap.get("date_facture_vente");
                if (dateFVCol != null) {
                    Cell dateCell = row.getCell(dateFVCol);
                    dateFacture = parseDateFromCell(dateCell);
                }
                if (dateFacture == null) {
                    String dateFV = getCellValue(row, columnMap, "date_facture_vente");
                    dateFacture = parseDate(dateFV);
                }
                newFv.setDateFacture(dateFacture);
                
                // Date échéance = date facture + 30 jours (défaut)
                if (dateFacture != null) {
                    newFv.setDateEcheance(dateFacture.plusDays(30));
                }
                
                newFv.setClientId(finalClientId);
                newFv.setEtatPaiement("non_regle");
                newFv.setLignes(new ArrayList<>());
                newFv.setPaiements(new ArrayList<>());
                return newFv;
            });
            
            // Stocker l'association FV -> BC
            fvToBcNumMap.put(finalNumeroFV, finalNumeroBC);
            
            // Ajouter la ligne à la facture vente
            LineItem fvLigne = createLineItemForFactureVente(row, columnMap, result);
            fvLignesMap.computeIfAbsent(finalNumeroFV, k -> new ArrayList<>()).add(fvLigne);
        }
    }
    
    private LineItem createLineItem(Row row, Map<String, Integer> columnMap, ImportResult result) {
        LineItem ligne = new LineItem();
        
        // Numéro article
        String numArticle = getCellValue(row, columnMap, "numero_article");
        ligne.setProduitRef(numArticle != null ? numArticle.trim() : null);
        
        // Désignation
        ligne.setDesignation(getCellValue(row, columnMap, "designation"));
        
        // Unité
        ligne.setUnite(getCellValue(row, columnMap, "unite"));
        
        // Quantités
        Double qteBC = getDoubleValue(row, columnMap, "quantite_bc");
        ligne.setQuantiteAchetee(qteBC != null ? qteBC.intValue() : 0);
        
        Double qteLivree = getDoubleValue(row, columnMap, "quantite_livree");
        ligne.setQuantiteVendue(qteLivree != null ? qteLivree.intValue() : ligne.getQuantiteAchetee());
        
        // Prix d'achat unitaire HT
        ligne.setPrixAchatUnitaireHT(getDoubleValue(row, columnMap, "prix_achat_unitaire_ht"));
        
        // Prix de vente unitaire HT
        Double prixVenteHT = getDoubleValue(row, columnMap, "prix_vente_unitaire_ht");
        if (prixVenteHT == null || prixVenteHT == 0) {
            // Calculer depuis prix vente TTC si disponible
            Double prixVenteTTC = getDoubleValue(row, columnMap, "prix_vente_unitaire_ttc");
            Double tauxTVA = getDoubleValue(row, columnMap, "taux_tva");
            if (prixVenteTTC != null && prixVenteTTC > 0 && tauxTVA != null) {
                prixVenteHT = prixVenteTTC / (1 + (tauxTVA / 100));
            }
        }
        ligne.setPrixVenteUnitaireHT(prixVenteHT != null ? prixVenteHT : 0.0);
        
        // TVA
        Double tva = getDoubleValue(row, columnMap, "taux_tva");
        ligne.setTva(tva != null ? tva : 20.0); // Défaut 20%
        
        // Calculer les totaux
        calculateLineItemTotals(ligne);
        
        return ligne;
    }
    
    private LineItem createLineItemForFacture(Row row, Map<String, Integer> columnMap, ImportResult result) {
        // Même logique que createLineItem mais pour facture achat
        return createLineItem(row, columnMap, result);
    }
    
    private LineItem createLineItemForFactureVente(Row row, Map<String, Integer> columnMap, ImportResult result) {
        // Même logique que createLineItem mais pour facture vente
        return createLineItem(row, columnMap, result);
    }
    
    private void calculateLineItemTotals(LineItem ligne) {
        // Total HT achat
        if (ligne.getQuantiteAchetee() != null && ligne.getPrixAchatUnitaireHT() != null) {
            ligne.setTotalHT(ligne.getQuantiteAchetee() * ligne.getPrixAchatUnitaireHT());
        }
        
        // Total TTC achat
        if (ligne.getTotalHT() != null && ligne.getTva() != null) {
            ligne.setTotalTTC(ligne.getTotalHT() * (1 + (ligne.getTva() / 100)));
        }
        
        // Marge unitaire
        if (ligne.getPrixVenteUnitaireHT() != null && ligne.getPrixAchatUnitaireHT() != null && ligne.getPrixAchatUnitaireHT() > 0) {
            ligne.setMargeUnitaire(ligne.getPrixVenteUnitaireHT() - ligne.getPrixAchatUnitaireHT());
            ligne.setMargePourcentage((ligne.getMargeUnitaire() / ligne.getPrixAchatUnitaireHT()) * 100);
        }
    }
    
    private void calculateBCTotals(BandeCommande bc) {
        if (bc.getLignes() == null || bc.getLignes().isEmpty()) {
            return;
        }
        
        double totalAchatHT = 0;
        double totalAchatTTC = 0;
        double totalVenteHT = 0;
        double totalVenteTTC = 0;
        
        for (LineItem ligne : bc.getLignes()) {
            if (ligne.getPrixAchatUnitaireHT() != null && ligne.getQuantiteAchetee() != null) {
                double ht = ligne.getPrixAchatUnitaireHT() * ligne.getQuantiteAchetee();
                totalAchatHT += ht;
                if (ligne.getTva() != null) {
                    totalAchatTTC += ht * (1 + (ligne.getTva() / 100));
                }
            }
            
            if (ligne.getPrixVenteUnitaireHT() != null && ligne.getQuantiteVendue() != null) {
                double ht = ligne.getPrixVenteUnitaireHT() * ligne.getQuantiteVendue();
                totalVenteHT += ht;
                if (ligne.getTva() != null) {
                    totalVenteTTC += ht * (1 + (ligne.getTva() / 100));
                }
            }
        }
        
        bc.setTotalAchatHT(totalAchatHT);
        bc.setTotalAchatTTC(totalAchatTTC);
        bc.setTotalVenteHT(totalVenteHT);
        bc.setTotalVenteTTC(totalVenteTTC);
        bc.setMargeTotale(totalVenteHT - totalAchatHT);
        
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(((totalVenteHT - totalAchatHT) / totalAchatHT) * 100);
        }
    }
    
    private void calculateFactureAchatTotals(FactureAchat fa) {
        if (fa.getLignes() == null || fa.getLignes().isEmpty()) {
            return;
        }
        
        double totalHT = 0;
        double totalTVA = 0;
        double totalTTC = 0;
        
        for (LineItem ligne : fa.getLignes()) {
            if (ligne.getTotalHT() != null) {
                totalHT += ligne.getTotalHT();
            }
            if (ligne.getTotalTTC() != null && ligne.getTotalHT() != null) {
                totalTVA += ligne.getTotalTTC() - ligne.getTotalHT();
                totalTTC += ligne.getTotalTTC();
            }
        }
        
        fa.setTotalHT(totalHT);
        fa.setTotalTVA(totalTVA);
        fa.setTotalTTC(totalTTC);
        fa.setMontantRestant(totalTTC); // Pas encore payé
    }
    
    private void calculateFactureVenteTotals(FactureVente fv) {
        if (fv.getLignes() == null || fv.getLignes().isEmpty()) {
            return;
        }
        
        double totalHT = 0;
        double totalTVA = 0;
        double totalTTC = 0;
        
        for (LineItem ligne : fv.getLignes()) {
            // Pour facture vente, utiliser quantite vendue et prix vente
            if (ligne.getPrixVenteUnitaireHT() != null && ligne.getQuantiteVendue() != null) {
                double ht = ligne.getPrixVenteUnitaireHT() * ligne.getQuantiteVendue();
                totalHT += ht;
                if (ligne.getTva() != null) {
                    double ttc = ht * (1 + (ligne.getTva() / 100));
                    totalTVA += ttc - ht;
                    totalTTC += ttc;
                }
            }
        }
        
        fv.setTotalHT(totalHT);
        fv.setTotalTVA(totalTVA);
        fv.setTotalTTC(totalTTC);
        fv.setMontantRestant(totalTTC); // Pas encore payé
    }
    
    private String findOrCreateClient(String ice, String nom, ImportResult result) {
        // Ne pas rechercher si le nom est "Inconnu" - créer directement
        boolean shouldSkipSearch = (nom != null && nom.trim().equalsIgnoreCase("inconnu"));
        
        if (!shouldSkipSearch && ice != null && !ice.trim().isEmpty()) {
            Optional<Client> existing = clientRepository.findByIce(ice.trim());
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }
        
        if (!shouldSkipSearch && nom != null && !nom.trim().isEmpty() && !nom.trim().equalsIgnoreCase("inconnu")) {
            final String finalNom = nom.trim(); // Copie finale pour lambda
            Optional<Client> existing = clientRepository.findAll().stream()
                    .filter(c -> c.getNom() != null && c.getNom().equalsIgnoreCase(finalNom))
                    .findFirst();
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }
        
        // Créer nouveau client même si le nom est "Inconnu" ou vide
        if (nom == null || nom.trim().isEmpty() || nom.trim().equalsIgnoreCase("inconnu")) {
            // Si on a un ICE, utiliser "Client [ICE]", sinon générer un nom unique
            if (ice != null && !ice.trim().isEmpty()) {
                nom = "Client " + ice.trim();
            } else {
                nom = "Client Import " + System.currentTimeMillis();
            }
        }
        
        Client newClient = Client.builder()
                .nom(nom.trim())
                .ice(ice != null ? ice.trim() : null)
                .email("")
                .telephone("")
                .adresse("")
                .contacts(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Client saved = clientRepository.save(newClient);
        result.getWarnings().add("Client créé automatiquement: " + nom.trim());
        return saved.getId();
    }
    
    private String findOrCreateFournisseur(String nom, ImportResult result) {
        // Ne pas créer de fournisseur si le nom est vraiment vide (sans même "Inconnu")
        // Mais créer si c'est "Inconnu" pour avoir quelque chose
        boolean shouldSkipSearch = (nom != null && nom.trim().equalsIgnoreCase("inconnu"));
        
        if (nom == null || nom.trim().isEmpty()) {
            nom = "Fournisseur Import " + System.currentTimeMillis();
        } else if (nom.trim().equalsIgnoreCase("inconnu")) {
            // Générer un nom unique pour éviter les conflits
            nom = "Fournisseur Import " + System.currentTimeMillis();
        }
        
        // Ne pas rechercher si c'était "Inconnu" - créer directement
        if (!shouldSkipSearch) {
            final String finalNom = nom.trim(); // Copie finale pour lambda
            Optional<Supplier> existing = supplierRepository.findAll().stream()
                    .filter(f -> f.getNom() != null && f.getNom().equalsIgnoreCase(finalNom))
                    .findFirst();
            
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }
        
        // Créer nouveau fournisseur
        Supplier newSupplier = Supplier.builder()
                .nom(nom.trim())
                .ice("")
                .email("")
                .telephone("")
                .adresse("")
                .modesPaiementAcceptes(Arrays.asList("virement", "cheque", "LCN", "compensation"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Supplier saved = supplierRepository.save(newSupplier);
        result.getWarnings().add("Fournisseur créé automatiquement: " + nom.trim());
        return saved.getId();
    }
    
    private String extractBCNumFromFacture(String numeroFacture, Map<String, BandeCommande> bcMap) {
        // Chercher dans les BCs importées pour trouver celle qui correspond à cette facture
        // Cette méthode sera améliorée selon les besoins métier
        // Pour l'instant, on retourne null car la liaison se fait via les lignes lors du processRow
        return null;
    }
    
    
    private String getCellValue(Row row, Map<String, Integer> columnMap, String columnName) {
        Integer colIndex = columnMap.get(columnName);
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        
        return getCellStringValue(cell);
    }
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // Convertir directement en LocalDate puis en String formatée
                        Date date = cell.getDateCellValue();
                        LocalDate localDate = date.toInstant()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate();
                        return localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    }
                    // Gérer les nombres entiers
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // Pour les formules, récupérer la valeur calculée
                    switch (cell.getCachedFormulaResultType()) {
                        case STRING:
                            return cell.getStringCellValue().trim();
                        case NUMERIC:
                            // Vérifier si c'est une date
                            if (DateUtil.isCellDateFormatted(cell)) {
                                Date date = cell.getDateCellValue();
                                LocalDate localDate = date.toInstant()
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate();
                                return localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                            }
                            double formulaValue = cell.getNumericCellValue();
                            if (formulaValue == (long) formulaValue) {
                                return String.valueOf((long) formulaValue);
                            }
                            return String.valueOf(formulaValue);
                        case BOOLEAN:
                            return String.valueOf(cell.getBooleanCellValue());
                        default:
                            return "";
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            log.warn("Error getting cell value: {}", e.getMessage());
            return "";
        }
    }
    
    private Double getDoubleValue(Row row, Map<String, Integer> columnMap, String columnName) {
        Integer colIndex = columnMap.get(columnName);
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return cell.getNumericCellValue();
                case STRING:
                    String strValue = cell.getStringCellValue().trim();
                    if (strValue.isEmpty()) return null;
                    // Gérer format français avec virgule
                    strValue = strValue.replace(" ", "").replace(",", ".");
                    try {
                        return Double.parseDouble(strValue);
                    } catch (NumberFormatException e) {
                        // Essayer avec le format français
                        try {
                            return FRENCH_NUMBER_FORMAT.parse(strValue.replace(".", ",")).doubleValue();
                        } catch (ParseException e2) {
                            log.warn("Cannot parse number: {}", strValue);
                            return null;
                        }
                    }
                case FORMULA:
                    return cell.getNumericCellValue();
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("Error parsing double from cell: {}", e.getMessage());
            return null;
        }
    }
    
    private Integer getIntegerValue(Row row, Map<String, Integer> columnMap, String columnName) {
        Integer colIndex = columnMap.get(columnName);
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return (int) cell.getNumericCellValue();
                case STRING:
                    String strValue = cell.getStringCellValue().trim();
                    if (strValue.isEmpty()) return null;
                    // Retirer les espaces
                    strValue = strValue.replace(" ", "");
                    try {
                        return Integer.parseInt(strValue);
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse integer: {}", strValue);
                        return null;
                    }
                case FORMULA:
                    return (int) cell.getNumericCellValue();
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("Error getting integer value: {}", e.getMessage());
            return null;
        }
    }
    
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        
        dateStr = dateStr.trim();
        
        try {
            // Format DD/MM/YYYY
            return LocalDate.parse(dateStr, DATE_FORMATTER_DDMMYYYY);
        } catch (DateTimeParseException e) {
            try {
                // Format YYYY-MM-DD
                return LocalDate.parse(dateStr, DATE_FORMATTER_YYYYMMDD);
            } catch (DateTimeParseException e2) {
                try {
                    // Format Excel (si c'est un nombre)
                    double excelDate = Double.parseDouble(dateStr);
                    Date javaDate = DateUtil.getJavaDate(excelDate);
                    return javaDate.toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                } catch (Exception e3) {
                    // Essayer de parser les formats de date Java comme "Thu Jan 02 00:00:00 CET 2025"
                    try {
                        // Format simple date
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH);
                        Date parsed = sdf.parse(dateStr);
                        return parsed.toInstant()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate();
                    } catch (Exception e4) {
                        log.warn("Cannot parse date: {}", dateStr);
                        return null;
                    }
                }
            }
        }
    }
    
    /**
     * Génère un fichier Excel modèle pour l'import
     */
    public byte[] generateTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Historique BC");
            
            // Style pour l'en-tête
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Créer l'en-tête
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "DATE BC", "N° FAC VTE", "DATE FAC VTE", "ICE", "N FAC FRS", "FRS", "CLENT",
                "N° BC", "N° ARTICLE", "DESIGNATION", "U", "QT BC", "PRIX ACHAT U HT",
                "PRIX ACHAT T HT", "TX TVA", "FACTURE ACHAT TTC", "PRIX ACHAT U TTC",
                "PRIX DE VENTE U TTC", "MARGE U TTC", "QT LIVREE", "PRIX DE VENTE U HT",
                "FACTURE VENTE TTC", "VERIFICATION TTC FACTURE VENTE"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Créer une ligne d'exemple
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("02/01/2025"); // DATE BC
            exampleRow.createCell(1).setCellValue("0101/2025"); // N° FAC VTE
            exampleRow.createCell(2).setCellValue("02/01/2025"); // DATE FAC VTE
            exampleRow.createCell(3).setCellValue("123456789"); // ICE
            exampleRow.createCell(4).setCellValue("FA-2025-001"); // N FAC FRS
            exampleRow.createCell(5).setCellValue("Nom Fournisseur"); // FRS
            exampleRow.createCell(6).setCellValue("Nom Client"); // CLENT
            exampleRow.createCell(7).setCellValue("BF4-BC-2025-0001"); // N° BC
            exampleRow.createCell(8).setCellValue("ART-001"); // N° ARTICLE
            exampleRow.createCell(9).setCellValue("Désignation produit"); // DESIGNATION
            exampleRow.createCell(10).setCellValue("sac"); // U
            exampleRow.createCell(11).setCellValue(100); // QT BC
            exampleRow.createCell(12).setCellValue(120.0); // PRIX ACHAT U HT
            exampleRow.createCell(13).setCellValue(12000.0); // PRIX ACHAT T HT
            exampleRow.createCell(14).setCellValue(20.0); // TX TVA
            exampleRow.createCell(15).setCellValue(14400.0); // FACTURE ACHAT TTC
            exampleRow.createCell(16).setCellValue(144.0); // PRIX ACHAT U TTC
            exampleRow.createCell(17).setCellValue(180.0); // PRIX DE VENTE U TTC
            exampleRow.createCell(18).setCellValue(36.0); // MARGE U TTC
            exampleRow.createCell(19).setCellValue(100); // QT LIVREE
            exampleRow.createCell(20).setCellValue(150.0); // PRIX DE VENTE U HT
            exampleRow.createCell(21).setCellValue(18000.0); // FACTURE VENTE TTC
            exampleRow.createCell(22).setCellValue(18000.0); // VERIFICATION TTC FACTURE VENTE
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Limiter la largeur max à 50
                int width = sheet.getColumnWidth(i);
                if (width > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }
            
            // Écrire dans un ByteArrayOutputStream
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating Excel template", e);
            throw new RuntimeException("Erreur lors de la génération du modèle Excel", e);
        }
    }
    
    /**
     * Génère un fichier Excel modèle pour l'import du catalogue produit
     */
    public byte[] generateProductCatalogTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Catalogue Produits");
            
            // Style pour l'en-tête
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Créer l'en-tête
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "REF ARTICLE", "DESIGNATION", "UNITE", "PRIX ACHAT U HT", 
                "PRIX VENTE U HT", "TVA (%)", "FOURNISSEUR", "QUANTITE STOCK"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Créer des lignes d'exemple
            Row exampleRow1 = sheet.createRow(1);
            exampleRow1.createCell(0).setCellValue("ART-001"); // REF ARTICLE
            exampleRow1.createCell(1).setCellValue("Ciment Portland 42.5"); // DESIGNATION
            exampleRow1.createCell(2).setCellValue("Sac"); // UNITE
            exampleRow1.createCell(3).setCellValue(50.0); // PRIX ACHAT U HT
            exampleRow1.createCell(4).setCellValue(65.0); // PRIX VENTE U HT
            exampleRow1.createCell(5).setCellValue(20.0); // TVA (%)
            exampleRow1.createCell(6).setCellValue("Fournisseur Ciment"); // FOURNISSEUR
            exampleRow1.createCell(7).setCellValue(100); // QUANTITE STOCK
            
            Row exampleRow2 = sheet.createRow(2);
            exampleRow2.createCell(0).setCellValue("ART-002");
            exampleRow2.createCell(1).setCellValue("Sable fin");
            exampleRow2.createCell(2).setCellValue("M3");
            exampleRow2.createCell(3).setCellValue(120.0);
            exampleRow2.createCell(4).setCellValue(150.0);
            exampleRow2.createCell(5).setCellValue(20.0);
            exampleRow2.createCell(6).setCellValue("Fournisseur Matériaux");
            exampleRow2.createCell(7).setCellValue(50);
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Limiter la largeur max à 50
                int width = sheet.getColumnWidth(i);
                if (width > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }
            
            // Écrire dans un ByteArrayOutputStream
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating product catalog Excel template", e);
            throw new RuntimeException("Erreur lors de la génération du modèle Excel catalogue produit", e);
        }
    }
    
    /**
     * Importe le catalogue produit depuis un fichier Excel
     */
    public ImportResult importProductCatalog(MultipartFile file) {
        ImportResult result = new ImportResult();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.getErrors().add("Le fichier Excel est vide ou ne contient que l'en-tête");
                return result;
            }
            
            // Mapping des colonnes
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnMap = new HashMap<>();
            for (Cell cell : headerRow) {
                if (cell == null) continue;
                String cellValue = getCellStringValue(cell).toLowerCase().trim();
                int colIndex = cell.getColumnIndex();
                
                if (cellValue.contains("ref") && cellValue.contains("article")) {
                    columnMap.put("ref_article", colIndex);
                } else if (cellValue.contains("designation")) {
                    columnMap.put("designation", colIndex);
                } else if (cellValue.contains("unite") || cellValue.equals("u")) {
                    columnMap.put("unite", colIndex);
                } else if (cellValue.contains("prix") && cellValue.contains("achat")) {
                    columnMap.put("prix_achat", colIndex);
                } else if (cellValue.contains("prix") && cellValue.contains("vente")) {
                    columnMap.put("prix_vente", colIndex);
                } else if (cellValue.contains("tva")) {
                    columnMap.put("tva", colIndex);
                } else if (cellValue.contains("fournisseur")) {
                    columnMap.put("fournisseur", colIndex);
                } else if (cellValue.contains("quantite") || cellValue.contains("stock")) {
                    columnMap.put("quantite_stock", colIndex);
                }
            }
            
            result.setTotalRows(sheet.getLastRowNum());
            int successCount = 0;
            int errorCount = 0;
            
            // Traiter chaque ligne
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    String refArticle = getCellValue(row, columnMap, "ref_article");
                    if (refArticle == null || refArticle.trim().isEmpty()) {
                        errorCount++;
                        result.getErrors().add(String.format("Ligne %d: Référence article manquante", i + 1));
                        continue;
                    }
                    
                    String designation = getCellValue(row, columnMap, "designation");
                    if (designation == null || designation.trim().isEmpty()) {
                        errorCount++;
                        result.getErrors().add(String.format("Ligne %d: Désignation manquante", i + 1));
                        continue;
                    }
                    
                    String unite = getCellValue(row, columnMap, "unite");
                    if (unite == null || unite.trim().isEmpty()) {
                        unite = "U"; // Valeur par défaut
                    }
                    
                    Double prixAchat = getDoubleValue(row, columnMap, "prix_achat");
                    if (prixAchat == null || prixAchat < 0) {
                        errorCount++;
                        result.getErrors().add(String.format("Ligne %d: Prix achat invalide", i + 1));
                        continue;
                    }
                    
                    Double prixVente = getDoubleValue(row, columnMap, "prix_vente");
                    if (prixVente == null || prixVente < 0) {
                        errorCount++;
                        result.getErrors().add(String.format("Ligne %d: Prix vente invalide", i + 1));
                        continue;
                    }
                    
                    Double tva = getDoubleValue(row, columnMap, "tva");
                    if (tva == null) {
                        tva = 20.0; // Valeur par défaut
                    }
                    
                    String fournisseurNom = getCellValue(row, columnMap, "fournisseur");
                    String fournisseurId = null;
                    if (fournisseurNom != null && !fournisseurNom.trim().isEmpty()) {
                        // Chercher ou créer le fournisseur
                        Supplier supplier = supplierRepository.findByNom(fournisseurNom.trim())
                            .orElse(null);
                        if (supplier == null) {
                            supplier = Supplier.builder()
                                .nom(fournisseurNom.trim())
                                .build();
                            supplier = supplierRepository.save(supplier);
                        }
                        fournisseurId = supplier.getId();
                    }
                    
                    Integer quantiteStock = getIntegerValue(row, columnMap, "quantite_stock");
                    if (quantiteStock == null) {
                        quantiteStock = 0;
                    }
                    
                    // Vérifier si le produit existe déjà
                    Product existingProduct = productRepository.findByRefArticle(refArticle.trim())
                        .orElse(null);
                    
                    Product product;
                    if (existingProduct != null) {
                        // Mettre à jour le produit existant
                        existingProduct.setDesignation(designation.trim());
                        existingProduct.setUnite(unite.trim());
                        existingProduct.setPrixAchatUnitaireHT(prixAchat);
                        existingProduct.setPrixVenteUnitaireHT(prixVente);
                        existingProduct.setTva(tva);
                        existingProduct.setFournisseurId(fournisseurId);
                        existingProduct.setQuantiteEnStock(quantiteStock);
                        existingProduct.setUpdatedAt(LocalDateTime.now());
                        product = productRepository.save(existingProduct);
                    } else {
                        // Créer un nouveau produit
                        product = Product.builder()
                            .refArticle(refArticle.trim())
                            .designation(designation.trim())
                            .unite(unite.trim())
                            .prixAchatUnitaireHT(prixAchat)
                            .prixVenteUnitaireHT(prixVente)
                            .tva(tva)
                            .fournisseurId(fournisseurId)
                            .quantiteEnStock(quantiteStock)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                        product = productRepository.save(product);
                    }
                    
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, e.getMessage()));
                    log.error("Error processing product row {}: {}", i + 1, e.getMessage(), e);
                }
            }
            
            result.setSuccessCount(successCount);
            result.setErrorCount(errorCount);
            
        } catch (Exception e) {
            log.error("Error importing product catalog", e);
            result.getErrors().add("Erreur lors de l'import: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Parse une date directement depuis une cellule Excel
     */
    private LocalDate parseDateFromCell(Cell cell) {
        if (cell == null) return null;
        
        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return date.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            } else if (cell.getCellType() == CellType.NUMERIC) {
                // Date stockée comme nombre Excel
                double excelDate = cell.getNumericCellValue();
                Date javaDate = DateUtil.getJavaDate(excelDate);
                return javaDate.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            } else if (cell.getCellType() == CellType.STRING) {
                // Parser depuis string
                return parseDate(cell.getStringCellValue());
            }
        } catch (Exception e) {
            log.warn("Error parsing date from cell: {}", e.getMessage());
        }
        return null;
    }
}
