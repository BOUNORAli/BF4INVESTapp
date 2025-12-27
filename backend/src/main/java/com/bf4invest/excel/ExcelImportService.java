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
    private final OperationComptableRepository operationComptableRepository;
    private final com.bf4invest.service.PaiementService paiementService;
    
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
                    
                    // Gérer le format "mois/année" (ex: "8/2025" -> extraire 8)
                    if (strValue.contains("/")) {
                        String[] parts = strValue.split("/");
                        if (parts.length >= 1) {
                            try {
                                return Integer.parseInt(parts[0].trim());
                            } catch (NumberFormatException e) {
                                // Continuer avec le parsing normal
                            }
                        }
                    }
                    
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
    
    /**
     * Génère un fichier Excel modèle pour l'import des opérations comptables
     */
    public byte[] generateOperationsComptablesTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Opérations Comptables");
            
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
                "AFFECTATION/N°BC", "RELEVE BQ", "CONTRE PARTIE", "NOM CLIENT/FRS", "Client/Fourn",
                "SOURCE payement", "DATE", "TYPE", "N° FACTURE", "REFERENCE",
                "TOTAL TTC APRES RG", "Total payement TTC", "Taux TVA", "TAUX RG",
                "Moyen de payement", "COMMENT AIRE", "tva mois", "annee", "mois",
                "SOLDE BANQUE", "TOTAL TTC APRES RG (calculé)", "Total payement TTC (calculé)",
                "RG TTC", "RG HT", "FACTURE HT YC RG", "HT PAYE", "TVA FACTURE YC RG", "TVA", "bilan", "CA"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Créer des lignes d'exemple
            Row exampleRow1 = sheet.createRow(1);
            exampleRow1.createCell(0).setCellValue("1001C/22"); // AFFECTATION/N°BC
            exampleRow1.createCell(1).setCellValue("BM"); // RELEVE BQ
            exampleRow1.createCell(2).setCellValue("ARTIS"); // CONTRE PARTIE
            exampleRow1.createCell(3).setCellValue("ABNIMO SER"); // NOM CLIENT/FRS
            exampleRow1.createCell(4).setCellValue("C"); // Client/Fourn
            exampleRow1.createCell(5).setCellValue("BM"); // SOURCE payement
            exampleRow1.createCell(6).setCellValue("31/10/2022"); // DATE
            exampleRow1.createCell(7).setCellValue("Facture"); // TYPE
            exampleRow1.createCell(8).setCellValue("1004/2022"); // N° FACTURE
            exampleRow1.createCell(9).setCellValue(""); // REFERENCE
            exampleRow1.createCell(10).setCellValue(7920.0); // TOTAL TTC APRES RG
            exampleRow1.createCell(11).setCellValue(0.0); // Total payement TTC
            exampleRow1.createCell(12).setCellValue(20.0); // Taux TVA
            exampleRow1.createCell(13).setCellValue(0.0); // TAUX RG
            exampleRow1.createCell(14).setCellValue(""); // Moyen de payement
            exampleRow1.createCell(15).setCellValue("LIV G1 17/08"); // COMMENT AIRE
            exampleRow1.createCell(16).setCellValue(12); // tva mois
            exampleRow1.createCell(17).setCellValue(2022); // annee
            exampleRow1.createCell(18).setCellValue(10); // mois
            exampleRow1.createCell(19).setCellValue(0.0); // SOLDE BANQUE
            exampleRow1.createCell(20).setCellValue(7920.0); // TOTAL TTC APRES RG (calculé)
            exampleRow1.createCell(21).setCellValue(0.0); // Total payement TTC (calculé)
            exampleRow1.createCell(22).setCellValue(0.0); // RG TTC
            exampleRow1.createCell(23).setCellValue(0.0); // RG HT
            exampleRow1.createCell(24).setCellValue(6600.0); // FACTURE HT YC RG
            exampleRow1.createCell(25).setCellValue(0.0); // HT PAYE
            exampleRow1.createCell(26).setCellValue(1320.0); // TVA FACTURE YC RG
            exampleRow1.createCell(27).setCellValue(1320.0); // TVA
            exampleRow1.createCell(28).setCellValue("6600,00 C"); // bilan
            exampleRow1.createCell(29).setCellValue(""); // CA
            
            Row exampleRow2 = sheet.createRow(2);
            exampleRow2.createCell(0).setCellValue("0508A/23"); // AFFECTATION/N°BC
            exampleRow2.createCell(1).setCellValue("BM"); // RELEVE BQ
            exampleRow2.createCell(2).setCellValue("PLASTIMA"); // CONTRE PARTIE
            exampleRow2.createCell(3).setCellValue("ACCESS DE"); // NOM CLIENT/FRS
            exampleRow2.createCell(4).setCellValue("F"); // Client/Fourn
            exampleRow2.createCell(5).setCellValue("Caisse"); // SOURCE payement
            exampleRow2.createCell(6).setCellValue("20/07/2023"); // DATE
            exampleRow2.createCell(7).setCellValue("Paiement"); // TYPE
            exampleRow2.createCell(8).setCellValue(""); // N° FACTURE
            exampleRow2.createCell(9).setCellValue("AVM 741498"); // REFERENCE
            exampleRow2.createCell(10).setCellValue(0.0); // TOTAL TTC APRES RG
            exampleRow2.createCell(11).setCellValue(45520.0); // Total payement TTC
            exampleRow2.createCell(12).setCellValue(20.0); // Taux TVA
            exampleRow2.createCell(13).setCellValue(0.0); // TAUX RG
            exampleRow2.createCell(14).setCellValue("LCN"); // Moyen de payement
            exampleRow2.createCell(15).setCellValue("LIV AOUT"); // COMMENT AIRE
            exampleRow2.createCell(16).setCellValue(7); // tva mois
            exampleRow2.createCell(17).setCellValue(2023); // annee
            exampleRow2.createCell(18).setCellValue(7); // mois
            exampleRow2.createCell(19).setCellValue(200000.0); // SOLDE BANQUE
            exampleRow2.createCell(20).setCellValue(0.0); // TOTAL TTC APRES RG (calculé)
            exampleRow2.createCell(21).setCellValue(45520.0); // Total payement TTC (calculé)
            exampleRow2.createCell(22).setCellValue(0.0); // RG TTC
            exampleRow2.createCell(23).setCellValue(0.0); // RG HT
            exampleRow2.createCell(24).setCellValue(0.0); // FACTURE HT YC RG
            exampleRow2.createCell(25).setCellValue(37933.33); // HT PAYE
            exampleRow2.createCell(26).setCellValue(0.0); // TVA FACTURE YC RG
            exampleRow2.createCell(27).setCellValue(7586.67); // TVA
            exampleRow2.createCell(28).setCellValue(""); // bilan
            exampleRow2.createCell(29).setCellValue(""); // CA
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
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
            log.error("Error generating operations comptables Excel template", e);
            throw new RuntimeException("Erreur lors de la génération du modèle Excel opérations comptables", e);
        }
    }
    
    /**
     * Importe les opérations comptables depuis un fichier Excel
     */
    public ImportResult importOperationsComptables(MultipartFile file) {
        ImportResult result = new ImportResult();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.getErrors().add("Le fichier Excel est vide ou ne contient que l'en-tête");
                return result;
            }
            
            // Mapping intelligent des colonnes
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnMap = mapOperationsComptablesColumns(headerRow);
            log.info("Mapped operations comptables columns: {}", columnMap);
            
            result.setTotalRows(sheet.getLastRowNum());
            int successCount = 0;
            int errorCount = 0;
            
            // Traiter chaque ligne
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // Vérifier si la ligne est vide (toutes les cellules vides)
                boolean isEmptyRow = true;
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    if (cell != null) {
                        String cellValue = getCellStringValue(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            isEmptyRow = false;
                            break;
                        }
                    }
                }
                if (isEmptyRow) continue;
                
                try {
                    OperationComptable operation = processOperationComptableRow(row, columnMap, result);
                    if (operation != null) {
                        operationComptableRepository.save(operation);
                        successCount++;
                    } else {
                        errorCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "Erreur inconnue lors du traitement de la ligne";
                    }
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMsg));
                    log.error("Error processing operation comptable row {}: {}", i + 1, errorMsg, e);
                }
            }
            
            result.setSuccessCount(successCount);
            result.setErrorCount(errorCount);
            
            // Phase post-import : traiter les paiements pour mettre à jour les statuts des factures
            if (successCount > 0) {
                try {
                    int paymentsProcessed = processPaymentsFromOperations(result);
                    log.info("Processed {} payments from imported operations", paymentsProcessed);
                } catch (Exception e) {
                    log.error("Error processing payments from operations", e);
                    result.getWarnings().add("Erreur lors du traitement des paiements: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Error importing operations comptables", e);
            result.getErrors().add("Erreur lors de l'import: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Traite les paiements depuis les opérations comptables importées
     * et met à jour les statuts des factures
     */
    private int processPaymentsFromOperations(ImportResult result) {
        final java.util.concurrent.atomic.AtomicInteger paymentsProcessed = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // Récupérer toutes les opérations de type PAIEMENT avec un montant de paiement > 0
        List<OperationComptable> allOperations = operationComptableRepository.findAll();
        log.info("Total operations in database: {}", allOperations.size());
        
        // Debug: compter les opérations par type
        long factureCount = allOperations.stream().filter(op -> op.getTypeMouvement() == TypeMouvement.FACTURE).count();
        long paiementCount = allOperations.stream().filter(op -> op.getTypeMouvement() == TypeMouvement.PAIEMENT).count();
        long nullTypeCount = allOperations.stream().filter(op -> op.getTypeMouvement() == null).count();
        log.info("Operations by type - FACTURE: {}, PAIEMENT: {}, NULL: {}", factureCount, paiementCount, nullTypeCount);
        
        // Filtrer les paiements - accepter même sans numéro de facture si on a une référence
        List<OperationComptable> paiements = allOperations.stream()
                .filter(op -> {
                    // Vérifier le type mouvement
                    boolean isPaiement = op.getTypeMouvement() == TypeMouvement.PAIEMENT;
                    if (!isPaiement && op.getTypeMouvement() == null) {
                        // Si type mouvement est null, vérifier si c'est un paiement par le montant
                        // Un paiement a totalPayementTtc > 0 et totalTtcApresRg == 0 ou null
                        isPaiement = op.getTotalPayementTtc() != null && op.getTotalPayementTtc() > 0
                                && (op.getTotalTtcApresRg() == null || op.getTotalTtcApresRg() == 0);
                    }
                    return isPaiement;
                })
                .filter(op -> op.getTotalPayementTtc() != null && op.getTotalPayementTtc() > 0)
                .filter(op -> op.getTypeOperation() == TypeOperation.C || op.getTypeOperation() == TypeOperation.F)
                .collect(java.util.stream.Collectors.toList());
        
        // Log détaillé pour debug
        if (paiements.isEmpty() && allOperations.size() > 0) {
            log.warn("No payments found. Sample operations: {}", allOperations.stream()
                    .limit(5)
                    .map(op -> String.format("typeMouvement=%s, totalPayementTtc=%s, totalTtcApresRg=%s, numeroFacture=%s, typeOperation=%s",
                            op.getTypeMouvement(), op.getTotalPayementTtc(), op.getTotalTtcApresRg(), 
                            op.getNumeroFacture(), op.getTypeOperation()))
                    .collect(java.util.stream.Collectors.joining("; ")));
        }
        
        log.info("Found {} payment operations to process", paiements.size());
        
        for (OperationComptable operation : paiements) {
            try {
                String numeroFactureTemp = operation.getNumeroFacture() != null ? operation.getNumeroFacture().trim() : null;
                String reference = operation.getReference() != null ? operation.getReference().trim() : null;
                TypeOperation typeOperation = operation.getTypeOperation();
                Double montantPaiementTemp = operation.getTotalPayementTtc();
                
                if (montantPaiementTemp == null || montantPaiementTemp <= 0) {
                    continue;
                }
                
                // Si pas de numéro de facture, essayer de l'extraire de la référence ou utiliser la référence comme numéro
                if (numeroFactureTemp == null || numeroFactureTemp.isEmpty()) {
                    if (reference != null && !reference.trim().isEmpty()) {
                        // Essayer d'utiliser la référence comme numéro de facture
                        numeroFactureTemp = reference.trim();
                        log.debug("Using reference as invoice number: {}", numeroFactureTemp);
                    } else {
                        log.debug("Skipping payment operation: no invoice number and no reference");
                        continue;
                    }
                }
                
                // Créer des copies finales pour les lambdas
                final String numeroFacture = numeroFactureTemp;
                final Double montantPaiement = montantPaiementTemp;
                
                // Déterminer le type de facture selon typeOperation
                if (typeOperation == TypeOperation.F) {
                    // Facture Achat (Fournisseur)
                    factureAchatRepository.findByNumeroFactureAchat(numeroFacture)
                            .ifPresentOrElse(
                                    facture -> {
                                        try {
                                            // Vérifier si un paiement existe déjà pour cette facture avec cette référence et date
                                            boolean paiementExiste = false;
                                            List<Paiement> paiementsExistants = paiementService.findByFactureAchatId(facture.getId());
                                            if (operation.getReference() != null && !operation.getReference().trim().isEmpty()) {
                                                // Vérifier par référence
                                                paiementExiste = paiementsExistants.stream()
                                                        .anyMatch(p -> operation.getReference().equals(p.getReference()));
                                            } else {
                                                // Si pas de référence, vérifier par date et montant (tolérance de 0.01 MAD)
                                                paiementExiste = paiementsExistants.stream()
                                                        .anyMatch(p -> p.getDate() != null && p.getDate().equals(operation.getDateOperation())
                                                                && p.getMontant() != null && Math.abs(p.getMontant() - montantPaiement) < 0.01);
                                            }
                                            
                                            if (!paiementExiste) {
                                                // Créer le paiement
                                                Paiement paiement = Paiement.builder()
                                                        .factureAchatId(facture.getId())
                                                        .bcReference(operation.getNumeroBc())
                                                        .typeMouvement("F")
                                                        .nature("paiement")
                                                        .date(operation.getDateOperation())
                                                        .montant(montantPaiement)
                                                        .mode(operation.getMoyenPayement())
                                                        .reference(operation.getReference())
                                                        .tvaRate(operation.getTauxTva())
                                                        .totalPaiementTTC(montantPaiement)
                                                        .htPaye(operation.getHtPaye())
                                                        .tvaPaye(operation.getTva())
                                                        .notes(operation.getCommentaire())
                                                        .build();
                                                
                                                // Créer le paiement (qui mettra à jour automatiquement le statut de la facture)
                                                paiementService.create(paiement);
                                                paymentsProcessed.incrementAndGet();
                                                log.info("Created payment for facture achat {}: {} MAD", numeroFacture, montantPaiement);
                                            } else {
                                                log.debug("Payment already exists for facture achat {} with reference {}", numeroFacture, operation.getReference());
                                            }
                                        } catch (Exception e) {
                                            log.error("Error creating payment for facture achat {}: {}", numeroFacture, e.getMessage(), e);
                                            result.getWarnings().add(String.format("Erreur création paiement facture achat %s: %s", numeroFacture, e.getMessage()));
                                        }
                                    },
                                    () -> {
                                        log.warn("Facture achat not found for numero: {}", numeroFacture);
                                        result.getWarnings().add(String.format("Facture achat non trouvée: %s", numeroFacture));
                                    }
                            );
                } else if (typeOperation == TypeOperation.C) {
                    // Facture Vente (Client)
                    factureVenteRepository.findByNumeroFactureVente(numeroFacture)
                            .ifPresentOrElse(
                                    facture -> {
                                        try {
                                            // Vérifier si un paiement existe déjà pour cette facture avec cette référence et date
                                            boolean paiementExiste = false;
                                            List<Paiement> paiementsExistants = paiementService.findByFactureVenteId(facture.getId());
                                            if (operation.getReference() != null && !operation.getReference().trim().isEmpty()) {
                                                // Vérifier par référence
                                                paiementExiste = paiementsExistants.stream()
                                                        .anyMatch(p -> operation.getReference().equals(p.getReference()));
                                            } else {
                                                // Si pas de référence, vérifier par date et montant (tolérance de 0.01 MAD)
                                                paiementExiste = paiementsExistants.stream()
                                                        .anyMatch(p -> p.getDate() != null && p.getDate().equals(operation.getDateOperation())
                                                                && p.getMontant() != null && Math.abs(p.getMontant() - montantPaiement) < 0.01);
                                            }
                                            
                                            if (!paiementExiste) {
                                                // Créer le paiement
                                                Paiement paiement = Paiement.builder()
                                                        .factureVenteId(facture.getId())
                                                        .bcReference(operation.getNumeroBc())
                                                        .typeMouvement("C")
                                                        .nature("paiement")
                                                        .date(operation.getDateOperation())
                                                        .montant(montantPaiement)
                                                        .mode(operation.getMoyenPayement())
                                                        .reference(operation.getReference())
                                                        .tvaRate(operation.getTauxTva())
                                                        .totalPaiementTTC(montantPaiement)
                                                        .htPaye(operation.getHtPaye())
                                                        .tvaPaye(operation.getTva())
                                                        .notes(operation.getCommentaire())
                                                        .build();
                                                
                                                // Créer le paiement (qui mettra à jour automatiquement le statut de la facture)
                                                paiementService.create(paiement);
                                                paymentsProcessed.incrementAndGet();
                                                log.info("Created payment for facture vente {}: {} MAD", numeroFacture, montantPaiement);
                                            } else {
                                                log.debug("Payment already exists for facture vente {} with reference {}", numeroFacture, operation.getReference());
                                            }
                                        } catch (Exception e) {
                                            log.error("Error creating payment for facture vente {}: {}", numeroFacture, e.getMessage(), e);
                                            result.getWarnings().add(String.format("Erreur création paiement facture vente %s: %s", numeroFacture, e.getMessage()));
                                        }
                                    },
                                    () -> {
                                        log.warn("Facture vente not found for numero: {}", numeroFacture);
                                        result.getWarnings().add(String.format("Facture vente non trouvée: %s", numeroFacture));
                                    }
                            );
                }
            } catch (Exception e) {
                log.error("Error processing payment operation: {}", e.getMessage(), e);
                result.getWarnings().add("Erreur traitement paiement: " + e.getMessage());
            }
        }
        
        return paymentsProcessed.get();
    }
    
    /**
     * Mapping intelligent des colonnes pour les opérations comptables
     */
    private Map<String, Integer> mapOperationsComptablesColumns(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        
        for (Cell cell : headerRow) {
            if (cell == null) continue;
            
            String cellValue = getCellStringValue(cell).toLowerCase().trim();
            if (cellValue.isEmpty()) continue;
            
            int colIndex = cell.getColumnIndex();
            String normalized = normalizeColumnName(cellValue);
            
            // Mapping des colonnes
            if (normalized.contains("affectation") || normalized.contains("bc") || normalized.contains("n°bc")) {
                map.put("numero_bc", colIndex);
            } else if (normalized.contains("releve") && normalized.contains("bq")) {
                map.put("releve_bancaire", colIndex);
            } else if (normalized.contains("contre") && normalized.contains("partie")) {
                map.put("contre_partie", colIndex);
            } else if (normalized.contains("nom") && (normalized.contains("client") || normalized.contains("frs"))) {
                map.put("nom_client_frs", colIndex);
            } else if (normalized.equals("client/fourn") || normalized.contains("client/fourn") || 
                       normalized.equals("clientfourn") || normalized.contains("type") && normalized.contains("operation")) {
                map.put("type_operation", colIndex);
            } else if (normalized.contains("source") && normalized.contains("payement")) {
                map.put("source_payement", colIndex);
            } else if (normalized.equals("date")) {
                map.put("date", colIndex);
            } else if (normalized.equals("type")) {
                map.put("type_mouvement", colIndex);
            } else if (normalized.contains("facture") && (normalized.contains("n°") || normalized.contains("numero"))) {
                map.put("numero_facture", colIndex);
            } else if (normalized.contains("reference")) {
                map.put("reference", colIndex);
            } else if (normalized.contains("total") && normalized.contains("ttc") && normalized.contains("apres") && normalized.contains("rg") && !normalized.contains("calcul")) {
                map.put("total_ttc_apres_rg", colIndex);
            } else if (normalized.contains("total") && normalized.contains("payement") && normalized.contains("ttc") && !normalized.contains("calcul")) {
                map.put("total_payement_ttc", colIndex);
            } else if (normalized.contains("taux") && normalized.contains("tva")) {
                map.put("taux_tva", colIndex);
            } else if (normalized.contains("taux") && normalized.contains("rg")) {
                map.put("taux_rg", colIndex);
            } else if (normalized.contains("moyen") && normalized.contains("payement")) {
                map.put("moyen_payement", colIndex);
            } else if (normalized.contains("comment") || normalized.contains("commentaire")) {
                map.put("commentaire", colIndex);
            } else if (normalized.contains("tva") && normalized.contains("mois")) {
                map.put("tva_mois", colIndex);
            } else if (normalized.equals("annee") || normalized.contains("annee")) {
                map.put("annee", colIndex);
            } else if (normalized.equals("mois")) {
                map.put("mois", colIndex);
            } else if (normalized.contains("solde") && normalized.contains("banque")) {
                map.put("solde_banque", colIndex);
            } else if (normalized.contains("total") && normalized.contains("ttc") && normalized.contains("apres") && normalized.contains("rg") && normalized.contains("calcul")) {
                map.put("total_ttc_apres_rg_calcule", colIndex);
            } else if (normalized.contains("total") && normalized.contains("payement") && normalized.contains("ttc") && normalized.contains("calcul")) {
                map.put("total_payement_ttc_calcule", colIndex);
            } else if (normalized.contains("rg") && normalized.contains("ttc") && !normalized.contains("ht")) {
                map.put("rg_ttc", colIndex);
            } else if (normalized.contains("rg") && normalized.contains("ht")) {
                map.put("rg_ht", colIndex);
            } else if (normalized.contains("facture") && normalized.contains("ht") && normalized.contains("yc") && normalized.contains("rg")) {
                map.put("facture_ht_yc_rg", colIndex);
            } else if (normalized.contains("ht") && normalized.contains("paye")) {
                map.put("ht_paye", colIndex);
            } else if (normalized.contains("tva") && normalized.contains("facture") && normalized.contains("yc") && normalized.contains("rg")) {
                map.put("tva_yc_rg", colIndex);
            } else if (normalized.equals("tva") && !normalized.contains("taux") && !normalized.contains("facture")) {
                map.put("tva", colIndex);
            } else if (normalized.equals("bilan")) {
                map.put("bilan", colIndex);
            } else if (normalized.equals("ca")) {
                map.put("ca", colIndex);
            }
        }
        
        return map;
    }
    
    /**
     * Traite une ligne d'opération comptable
     */
    private OperationComptable processOperationComptableRow(Row row, Map<String, Integer> columnMap, ImportResult result) {
        // Numéro BC (optionnel)
        String numeroBc = getCellValue(row, columnMap, "numero_bc");
        
        // Relevé bancaire
        String releveBancaire = getCellValue(row, columnMap, "releve_bancaire");
        
        // Contre partie
        String contrePartie = getCellValue(row, columnMap, "contre_partie");
        
        // Nom client/fournisseur
        String nomClientFrs = getCellValue(row, columnMap, "nom_client_frs");
        
        // Type opération (obligatoire)
        String typeOpStr = getCellValue(row, columnMap, "type_operation");
        if (typeOpStr == null || typeOpStr.trim().isEmpty()) {
            throw new RuntimeException("Type opération manquant (C, F, IS, TVA, CNSS, FB, LOY)");
        }
        TypeOperation typeOperation;
        try {
            typeOperation = TypeOperation.valueOf(typeOpStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Type opération invalide: " + typeOpStr + ". Valeurs possibles: C, F, IS, TVA, CNSS, FB, LOY");
        }
        
        // Source paiement
        String sourcePayement = getCellValue(row, columnMap, "source_payement");
        
        // Date (obligatoire)
        LocalDate dateOperation = null;
        Integer dateColIndex = columnMap.get("date");
        if (dateColIndex != null) {
            Cell dateCell = row.getCell(dateColIndex);
            if (dateCell != null) {
                dateOperation = parseDateFromCell(dateCell);
            }
        }
        if (dateOperation == null) {
            String dateStr = getCellValue(row, columnMap, "date");
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                dateOperation = parseDate(dateStr);
            }
        }
        if (dateOperation == null) {
            throw new RuntimeException("Date invalide ou manquante");
        }
        
        // Type mouvement
        String typeMouvStr = getCellValue(row, columnMap, "type_mouvement");
        TypeMouvement typeMouvement = null;
        if (typeMouvStr != null && !typeMouvStr.trim().isEmpty()) {
            String normalized = typeMouvStr.trim().toLowerCase();
            if (normalized.contains("facture")) {
                typeMouvement = TypeMouvement.FACTURE;
            } else if (normalized.contains("paiement") || normalized.contains("payment") || normalized.contains("pai")) {
                typeMouvement = TypeMouvement.PAIEMENT;
            }
        }
        
        // Si type mouvement est null, essayer de le déduire des montants
        // Un paiement a totalPayementTtc > 0 et (totalTtcApresRg == 0 ou null)
        if (typeMouvement == null) {
            Double totalPayement = getDoubleValue(row, columnMap, "total_payement_ttc");
            Double totalTtcApresRg = getDoubleValue(row, columnMap, "total_ttc_apres_rg");
            if (totalPayement != null && totalPayement > 0 && (totalTtcApresRg == null || totalTtcApresRg == 0)) {
                typeMouvement = TypeMouvement.PAIEMENT;
            } else if (totalTtcApresRg != null && totalTtcApresRg > 0) {
                typeMouvement = TypeMouvement.FACTURE;
            }
        }
        
        // Numéro facture
        String numeroFacture = getCellValue(row, columnMap, "numero_facture");
        
        // Référence
        String reference = getCellValue(row, columnMap, "reference");
        
        // Montants
        Double totalTtcApresRg = getDoubleValue(row, columnMap, "total_ttc_apres_rg");
        Double totalPayementTtc = getDoubleValue(row, columnMap, "total_payement_ttc");
        Double tauxTva = getDoubleValue(row, columnMap, "taux_tva");
        Double tauxRg = getDoubleValue(row, columnMap, "taux_rg");
        
        // Paiement
        String moyenPayement = getCellValue(row, columnMap, "moyen_payement");
        String commentaire = getCellValue(row, columnMap, "commentaire");
        
        // Période fiscale
        Integer tvaMois = getIntegerValue(row, columnMap, "tva_mois");
        Integer annee = getIntegerValue(row, columnMap, "annee");
        Integer mois = getIntegerValue(row, columnMap, "mois");
        
        // Soldes et calculs
        Double soldeBanque = getDoubleValue(row, columnMap, "solde_banque");
        Double totalTtcApresRgCalcule = getDoubleValue(row, columnMap, "total_ttc_apres_rg_calcule");
        Double totalPayementTtcCalcule = getDoubleValue(row, columnMap, "total_payement_ttc_calcule");
        Double rgTtc = getDoubleValue(row, columnMap, "rg_ttc");
        Double rgHt = getDoubleValue(row, columnMap, "rg_ht");
        Double factureHtYcRg = getDoubleValue(row, columnMap, "facture_ht_yc_rg");
        Double htPaye = getDoubleValue(row, columnMap, "ht_paye");
        Double tvaYcRg = getDoubleValue(row, columnMap, "tva_yc_rg");
        Double tva = getDoubleValue(row, columnMap, "tva");
        Double bilan = null;
        Integer bilanColIndex = columnMap.get("bilan");
        if (bilanColIndex != null) {
            Cell bilanCell = row.getCell(bilanColIndex);
            if (bilanCell != null) {
                try {
                    // Si c'est déjà un nombre, l'utiliser directement
                    if (bilanCell.getCellType() == CellType.NUMERIC) {
                        bilan = bilanCell.getNumericCellValue();
                    } else if (bilanCell.getCellType() == CellType.FORMULA) {
                        bilan = bilanCell.getNumericCellValue();
                    } else if (bilanCell.getCellType() == CellType.STRING) {
                        // Parser depuis une chaîne (ex: "6600,00 C" -> 6600.0, "-22 334,30 F" -> -22334.30)
                        String bilanStr = bilanCell.getStringCellValue().trim();
                        if (!bilanStr.isEmpty()) {
                            // Retirer tous les caractères sauf chiffres, virgule, point, moins et espaces
                            String numStr = bilanStr.replaceAll("[^0-9,.-\\s]", "").trim();
                            // Remplacer virgule par point pour le parsing
                            numStr = numStr.replace(",", ".");
                            // Retirer les espaces (séparateurs de milliers)
                            numStr = numStr.replace(" ", "");
                            if (!numStr.isEmpty()) {
                                bilan = Double.parseDouble(numStr);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignorer silencieusement si on ne peut pas parser
                }
            }
        }
        String ca = getCellValue(row, columnMap, "ca");
        
        // Créer l'opération comptable
        OperationComptable operation = OperationComptable.builder()
                .numeroBc(numeroBc)
                .releveBancaire(releveBancaire)
                .contrePartie(contrePartie)
                .nomClientFrs(nomClientFrs)
                .typeOperation(typeOperation)
                .sourcePayement(sourcePayement)
                .dateOperation(dateOperation)
                .typeMouvement(typeMouvement)
                .numeroFacture(numeroFacture)
                .reference(reference)
                .totalTtcApresRg(totalTtcApresRg)
                .totalPayementTtc(totalPayementTtc)
                .tauxTva(tauxTva)
                .tauxRg(tauxRg)
                .moyenPayement(moyenPayement)
                .commentaire(commentaire)
                .tvaMois(tvaMois)
                .annee(annee)
                .mois(mois)
                .soldeBanque(soldeBanque)
                .totalTtcApresRgCalcule(totalTtcApresRgCalcule)
                .totalPayementTtcCalcule(totalPayementTtcCalcule)
                .rgTtc(rgTtc)
                .rgHt(rgHt)
                .factureHtYcRg(factureHtYcRg)
                .htPaye(htPaye)
                .facture(null) // Pas utilisé dans le modèle actuel
                .tvaYcRg(tvaYcRg)
                .tva(tva)
                .bilan(bilan)
                .ca(ca)
                .createdAt(LocalDateTime.now())
                .build();
        
        return operation;
    }
}
