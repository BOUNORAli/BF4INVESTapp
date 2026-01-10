package com.bf4invest.excel;

import com.bf4invest.dto.ImportResult;
import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.DateFormatConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.BufferedInputStream;
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
    private final com.bf4invest.service.ChargeService chargeService;
    private final com.bf4invest.service.FactureAchatService factureAchatService;
    private final com.bf4invest.service.CompanyInfoService companyInfoService;
    private final com.bf4invest.service.BandeCommandeService bandeCommandeService;
    
    private static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_YYYYMMDD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final NumberFormat FRENCH_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    
    /**
     * Crée un Workbook à partir d'un InputStream en détectant automatiquement le format (.xls ou .xlsx)
     */
    private Workbook createWorkbook(InputStream is) throws Exception {
        try {
            // Utiliser BufferedInputStream pour permettre la détection automatique du format
            // WorkbookFactory détecte automatiquement le format (xls ou xlsx)
            if (!is.markSupported()) {
                is = new BufferedInputStream(is);
            }
            return WorkbookFactory.create(is);
        } catch (Exception e) {
            log.error("Error creating workbook: {}", e.getMessage(), e);
            throw new Exception("Format de fichier Excel non supporté ou fichier corrompu: " + e.getMessage(), e);
        }
    }
    
    public ImportResult importExcel(MultipartFile file) {
        ImportResult result = new ImportResult();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = createWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.getErrors().add("Le fichier Excel est vide ou ne contient que l'en-tête");
                return result;
            }
            
            // Mapping intelligent des colonnes
            Map<String, Integer> columnMap = mapColumnsIntelligent(sheet.getRow(0));
            log.info("Mapped columns: {}", columnMap);
            log.info("Total columns mapped: {}/19 expected columns", columnMap.size());
            
            // Vérifier les colonnes critiques
            if (!columnMap.containsKey("numero_bc")) {
                log.warn("WARNING: Column 'numero_bc' not found! Import may fail.");
            }
            if (!columnMap.containsKey("quantite_bc") && !columnMap.containsKey("quantite_livree")) {
                log.warn("WARNING: Neither 'quantite_bc' nor 'quantite_livree' found! Quantities may be 0.");
            }
            if (!columnMap.containsKey("prix_achat_unitaire_ht")) {
                log.warn("WARNING: Column 'prix_achat_unitaire_ht' not found! Purchase prices may be 0.");
            }
            
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
            
            // NOUVELLE STRUCTURE : Agrégation des produits par BC pour éviter les doublons
            // Map<BC_Numero, Map<ProduitKey, ProductAggregate>>
            // ProduitKey = refArticle + "|" + designation + "|" + unite
            Map<String, Map<String, ProductAggregate>> bcProductsMap = new HashMap<>();
            
            // Map pour gérer plusieurs factures vente par client/BC
            // Map<BC_Numero + "|" + clientId, List<FactureVente>>
            Map<String, List<FactureVente>> fvByBcAndClientMap = new HashMap<>();
            
            int processedRows = 0;
            int totalRows = sheet.getLastRowNum();
            log.info("Starting import of {} rows", totalRows);
            
            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    if (i % 100 == 0) {
                        log.info("Processed {}/{} rows (empty row skipped)", i, totalRows);
                    }
                    continue;
                }
                
                try {
                    processRow(row, columnMap, bcMap, faMap, fvMap, faLignesMap, fvLignesMap, 
                              faToBcNumMap, fvToBcNumMap, bcProductsMap, fvByBcAndClientMap, result);
                    processedRows++;
                    
                    // Log progression tous les 100 lignes
                    if (processedRows % 100 == 0) {
                        log.info("Processed {}/{} rows successfully ({} BCs, {} FAs, {} FVs)", 
                                processedRows, totalRows, bcMap.size(), faMap.size(), fvMap.size());
                    }
                    
                    // Stocker la ligne de succès (optionnel - limiter pour éviter OutOfMemory)
                    if (result.getSuccessRows().size() < 1000) { // Limiter à 1000 lignes de succès en mémoire
                        Map<String, Object> rowData = extractRowData(row, columnMap, sheet.getRow(0));
                        result.getSuccessRows().add(ImportResult.SuccessRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .build());
                    }
                } catch (OutOfMemoryError e) {
                    log.error("OutOfMemoryError at row {}: {}", i + 1, e.getMessage());
                    result.getErrors().add(String.format("Ligne %d: Mémoire insuffisante - import arrêté", i + 1));
                    result.setErrorCount(result.getErrorCount() + 1);
                    // Arrêter l'import si OutOfMemoryError
                    log.warn("Import stopped at row {} due to OutOfMemoryError. Processed {} rows successfully.", i + 1, processedRows);
                    break;
                } catch (Exception e) {
                    log.error("Error processing row {}: {}", i + 1, e.getMessage(), e);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "Erreur inconnue";
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMsg));
                    result.setErrorCount(result.getErrorCount() + 1);
                    
                    // Stocker la ligne en erreur avec ses données (limiter aussi)
                    if (result.getErrorRows().size() < 1000) {
                        Map<String, Object> rowData = extractRowData(row, columnMap, sheet.getRow(0));
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage(errorMsg)
                                .build());
                    }
                }
            }
            
            log.info("Finished processing rows. Total processed: {}, BCs: {}, FAs: {}, FVs: {}", 
                    processedRows, bcMap.size(), faMap.size(), fvMap.size());
            
            // NOUVELLE LOGIQUE : Convertir les ProductAggregate en LigneAchat et créer/mettre à jour les produits
            for (Map.Entry<String, BandeCommande> bcEntry : bcMap.entrySet()) {
                String bcNumero = bcEntry.getKey();
                BandeCommande bc = bcEntry.getValue();
                
                // Récupérer les agrégats de produits pour cette BC
                Map<String, ProductAggregate> productsMap = bcProductsMap.getOrDefault(bcNumero, new HashMap<>());
                
                // Convertir ProductAggregate en LigneAchat (une seule ligne par produit)
                List<LigneAchat> lignesAchat = new ArrayList<>();
                for (ProductAggregate agg : productsMap.values()) {
                    // Créer/mettre à jour le produit avec prix pondérés
                    createOrUpdateProductFromAggregate(agg, bc.getFournisseurId(), result);
                    
                    // Créer la LigneAchat
                    LigneAchat ligneAchat = LigneAchat.builder()
                        .produitRef(agg.produitRef)
                        .designation(agg.designation)
                        .unite(agg.unite != null ? agg.unite : "U")
                        .quantiteAchetee(agg.quantiteAcheteeTotale)
                        .prixAchatUnitaireHT(agg.getPrixAchatPondere())
                        .tva(agg.tva)
                        .build();
                    
                    // Calculer les totaux (arrondis)
                    if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getPrixAchatUnitaireHT() != null) {
                        ligneAchat.setTotalHT(NumberUtils.roundTo2Decimals(ligneAchat.getQuantiteAchetee() * ligneAchat.getPrixAchatUnitaireHT()));
                        if (ligneAchat.getTva() != null) {
                            ligneAchat.setTotalTTC(NumberUtils.roundTo2Decimals(ligneAchat.getTotalHT() * (1 + (ligneAchat.getTva() / 100.0))));
                        }
                    }
                    
                    lignesAchat.add(ligneAchat);
                }
                
                bc.setLignesAchat(lignesAchat);
                
                // Créer les ClientVente à partir des ventes agrégées
                List<ClientVente> clientsVente = new ArrayList<>();
                Map<String, ClientVente> clientVenteMap = new HashMap<>();
                
                for (ProductAggregate agg : productsMap.values()) {
                    for (Map.Entry<String, List<VenteInfo>> clientEntry : agg.ventesParClient.entrySet()) {
                        String clientId = clientEntry.getKey();
                        List<VenteInfo> ventes = clientEntry.getValue();
                        
                        ClientVente clientVente = clientVenteMap.computeIfAbsent(clientId, k -> {
                            ClientVente cv = new ClientVente();
                            cv.setClientId(clientId);
                            cv.setLignesVente(new ArrayList<>());
                            return cv;
                        });
                        
                        // Créer une LigneVente pour ce produit et ce client
                        // Agréger toutes les ventes de ce produit pour ce client
                        Double quantiteTotale = ventes.stream()
                            .mapToDouble(v -> v.quantite != null ? v.quantite : 0.0)
                            .sum();
                        Double sommePrix = ventes.stream()
                            .mapToDouble(v -> (v.quantite != null && v.prixUnitaireHT != null) ? v.quantite * v.prixUnitaireHT : 0.0)
                            .sum();
                        Double prixPondere = quantiteTotale > 0 ? sommePrix / quantiteTotale : null;
                        
                        LigneVente ligneVente = LigneVente.builder()
                            .produitRef(agg.produitRef)
                            .designation(agg.designation)
                            .unite(agg.unite != null ? agg.unite : "U")
                            .quantiteVendue(quantiteTotale)
                            .prixVenteUnitaireHT(prixPondere)
                            .tva(agg.tva)
                            .build();
                        
                        // Calculer les totaux (arrondis)
                        if (ligneVente.getQuantiteVendue() != null && ligneVente.getPrixVenteUnitaireHT() != null) {
                            ligneVente.setTotalHT(NumberUtils.roundTo2Decimals(ligneVente.getQuantiteVendue() * ligneVente.getPrixVenteUnitaireHT()));
                            if (ligneVente.getTva() != null) {
                                ligneVente.setTotalTTC(NumberUtils.roundTo2Decimals(ligneVente.getTotalHT() * (1 + (ligneVente.getTva() / 100.0))));
                            }
                        }
                        
                        clientVente.getLignesVente().add(ligneVente);
                    }
                }
                
                // Calculer les totaux pour chaque ClientVente
                for (ClientVente cv : clientVenteMap.values()) {
                    double totalHT = 0.0;
                    double totalTTC = 0.0;
                    double totalTVA = 0.0;
                    double margeTotale = 0.0;
                    
                    for (LigneVente lv : cv.getLignesVente()) {
                        if (lv.getTotalHT() != null) {
                            totalHT += lv.getTotalHT();
                        }
                        if (lv.getTotalTTC() != null) {
                            totalTTC += lv.getTotalTTC();
                        }
                        if (lv.getTva() != null && lv.getTotalHT() != null) {
                            totalTVA += lv.getTotalHT() * (lv.getTva() / 100.0);
                        }
                        
                        // Calculer la marge (prix vente - prix achat)
                        if (lv.getPrixVenteUnitaireHT() != null && lv.getQuantiteVendue() != null) {
                            // Trouver le prix d'achat correspondant
                            for (LigneAchat la : lignesAchat) {
                                if (la.getProduitRef() != null && la.getProduitRef().equals(lv.getProduitRef())) {
                                    if (la.getPrixAchatUnitaireHT() != null) {
                                        double margeUnitaire = NumberUtils.roundTo2Decimals(lv.getPrixVenteUnitaireHT() - la.getPrixAchatUnitaireHT());
                                        margeTotale += margeUnitaire * lv.getQuantiteVendue();
                                        lv.setMargeUnitaire(margeUnitaire);
                                        if (la.getPrixAchatUnitaireHT() > 0) {
                                            lv.setMargePourcentage(NumberUtils.roundTo2Decimals((margeUnitaire / la.getPrixAchatUnitaireHT()) * 100));
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    
                    cv.setTotalVenteHT(NumberUtils.roundTo2Decimals(totalHT));
                    cv.setTotalVenteTTC(NumberUtils.roundTo2Decimals(totalTTC));
                    cv.setTotalTVA(NumberUtils.roundTo2Decimals(totalTVA));
                    cv.setMargeTotale(NumberUtils.roundTo2Decimals(margeTotale));
                    if (totalHT > 0) {
                        cv.setMargePourcentage(NumberUtils.roundTo2Decimals((margeTotale / totalHT) * 100));
                    }
                }
                
                clientsVente.addAll(clientVenteMap.values());
                bc.setClientsVente(clientsVente);
                
                // IMPORTANT: Utiliser le service pour calculer les totaux correctement (gère multi-clients)
                // Ne pas utiliser calculateBCTotals() locale qui ne gère pas bien la structure multi-clients
                // Les totaux seront recalculés par le service lors de la sauvegarde
            }
            
            // Calculer les totaux et ajouter les lignes pour les factures
            for (FactureAchat fa : faMap.values()) {
                // Utiliser bcReference comme clé pour récupérer les lignes
                List<LineItem> lignes = faLignesMap.getOrDefault(fa.getBcReference(), new ArrayList<>());
                fa.setLignes(lignes);
                calculateFactureAchatTotals(fa);
            }
            
            for (FactureVente fv : fvMap.values()) {
                List<LineItem> lignes = fvLignesMap.getOrDefault(fv.getNumeroFactureVente(), new ArrayList<>());
                fv.setLignes(lignes);
                calculateFactureVenteTotals(fv);
            }
            
            // Sauvegarder les BC d'abord pour obtenir leurs IDs
            // IMPORTANT: Utiliser le service pour s'assurer que calculateTotals() est appelé correctement
            Map<String, String> bcNumToIdMap = new HashMap<>();
            for (BandeCommande bc : bcMap.values()) {
                try {
                    bc.setCreatedAt(LocalDateTime.now());
                    bc.setUpdatedAt(LocalDateTime.now());
                    
                    // Vérifier si BC existe déjà
                    Optional<BandeCommande> existing = bcRepository.findByNumeroBC(bc.getNumeroBC());
                    String bcId;
                    BandeCommande saved;
                    if (existing.isPresent()) {
                        // Mettre à jour la BC existante via le service (appelle calculateTotals)
                        BandeCommande existingBC = existing.get();
                        existingBC.setLignes(bc.getLignes());
                        existingBC.setLignesAchat(bc.getLignesAchat()); // Mettre à jour aussi lignesAchat
                        existingBC.setClientsVente(bc.getClientsVente()); // IMPORTANT: Mettre à jour clientsVente
                        existingBC.setDateBC(bc.getDateBC());
                        existingBC.setClientId(bc.getClientId());
                        existingBC.setFournisseurId(bc.getFournisseurId());
                        existingBC.setEtat("envoyee");
                        existingBC.setUpdatedAt(LocalDateTime.now());
                        
                        // Utiliser le service pour mettre à jour (appelle calculateTotals automatiquement)
                        saved = bandeCommandeService.update(existingBC.getId(), existingBC);
                        bcId = saved.getId();
                        result.getWarnings().add("BC " + bc.getNumeroBC() + " mise à jour");
                    } else {
                        // Créer via le service (appelle calculateTotals automatiquement)
                        saved = bandeCommandeService.create(bc);
                        bcId = saved.getId();
                    }
                    
                    bcNumToIdMap.put(bc.getNumeroBC(), bcId);
                    bc.setId(bcId); // Mettre à jour l'ID dans la map
                    
                    // Mettre à jour le stock automatiquement si ajouterAuStock est activé
                    // Récupérer la BC sauvegardée pour avoir toutes les données
                    BandeCommande savedBC = bcRepository.findById(bcId).orElse(bc);
                    if (Boolean.TRUE.equals(savedBC.getAjouterAuStock()) || savedBC.getAjouterAuStock() == null) {
                        // Par défaut, ajouter au stock si non spécifié
                        try {
                            bandeCommandeService.updateStockFromBC(savedBC);
                            log.debug("Stock mis à jour pour BC {}", bc.getNumeroBC());
                        } catch (Exception e) {
                            log.warn("Erreur lors de la mise à jour du stock pour BC {}: {}", 
                                bc.getNumeroBC(), e.getMessage());
                            result.getWarnings().add("Erreur mise à jour stock pour BC " + bc.getNumeroBC() + ": " + e.getMessage());
                        }
                    }
                    
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
                    // Lier à la BC en utilisant la map temporaire (utiliser bcReference comme clé)
                    String bcNum = fa.getBcReference();
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
                    
                    // Vérifier doublon par BC (une facture achat par BC)
                    if (fa.getBandeCommandeId() != null) {
                        List<FactureAchat> existingByBC = factureAchatRepository.findByBandeCommandeId(fa.getBandeCommandeId());
                        if (!existingByBC.isEmpty()) {
                            result.getWarnings().add("Facture Achat pour BC " + fa.getBcReference() + " déjà existante, ignorée");
                            continue;
                        }
                    }
                    
                    // Utiliser le service pour créer la facture (génère automatiquement le numéro)
                    // Le service génère le numéro si non fourni
                    factureAchatService.create(fa);
                    
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    log.error("Error saving FA for BC {}", fa.getBcReference(), e);
                    result.getErrors().add("Erreur sauvegarde FA pour BC " + fa.getBcReference() + ": " + e.getMessage());
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
        // Note: date_bc est maintenant optionnel dans la nouvelle structure
        List<String> priorityOrder = Arrays.asList(
            "numero_facture_fournisseur", // N FAC FRS avant FRS
            "numero_facture_vente",       // N FAC VTE avant autres
            "date_facture_vente",         // DATE FAC VTE (priorité car toujours présent)
            "date_facture_achat",         // Optionnel
            "numero_bc",
            "numero_article",
            "quantite_bc",
            "quantite_livree",
            "prix_achat_unitaire_ht",
            "prix_achat_total_ht",
            "prix_achat_ttc",
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
            
            // Lire la valeur brute de la cellule (préserver les retours à la ligne pour la normalisation)
            String rawCellValue = getCellStringValue(cell);
            if (rawCellValue == null || rawCellValue.trim().isEmpty()) continue;
            
            String cellValue = rawCellValue.toLowerCase();
            
            // Normaliser: retirer accents, espaces multiples, caractères spéciaux, retours à la ligne
            String normalized = normalizeColumnName(cellValue);
            
            // Log pour debug des colonnes importantes
            if (normalized.contains("prix") || normalized.contains("facture") || normalized.contains("quantite")) {
                log.debug("Header cell [{}]: raw='{}', normalized='{}'", colIndex, rawCellValue, normalized);
            }
            
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
        
        // Si numero_bc n'a pas été mappé, essayer de le détecter
        // Format 2021/2022: colonne "N BC" peut être en 3ème position
        // Format 2024: N BC peut être en première colonne sans en-tête
        if (!map.containsKey("numero_bc")) {
            // Chercher manuellement dans toutes les colonnes pour "n bc" ou variantes
            for (Cell cell : headerRow) {
                if (cell == null) continue;
                int colIndex = cell.getColumnIndex();
                if (usedColumns.contains(colIndex)) continue;
                
                String cellValue = getCellStringValue(cell).toLowerCase().trim();
                String normalized = normalizeColumnName(cellValue);
                
                // Chercher "n bc", "n° bc", "numero bc", etc. (format 2021/2022)
                if (normalized.equals("n bc") || normalized.equals("numero bc") || 
                    normalized.equals("no bc") || normalized.equals("num bc") ||
                    (normalized.startsWith("n") && normalized.contains("bc") && normalized.length() <= 10)) {
                    map.put("numero_bc", colIndex);
                    usedColumns.add(colIndex);
                    log.info("N° BC détecté dans la colonne '{}' (index {})", cellValue, colIndex);
                    break;
                }
            }
            
            // Si toujours pas trouvé, utiliser la première colonne comme fallback (format 2024)
            if (!map.containsKey("numero_bc")) {
                Cell firstCell = headerRow.getCell(0);
                String firstCellValue = firstCell != null ? getCellStringValue(firstCell).trim() : "";
                
                // Si la première colonne est vide ou très courte, considérer qu'elle peut contenir le N° BC
                // Le format typique d'un N° BC est comme "2YOU/23", "PDA01AC/24", etc.
                if (firstCellValue.isEmpty() || firstCellValue.length() < 10) {
                    // Vérifier si la première colonne ne ressemble pas à "EXERCICE FACTURATION" ou "VENTE PAR"
                    String normalizedFirst = normalizeColumnName(firstCellValue);
                    if (!normalizedFirst.contains("exercice") && !normalizedFirst.contains("facturation") && 
                        !normalizedFirst.contains("vente par")) {
                        map.put("numero_bc", 0);
                        usedColumns.add(0);
                        log.info("N° BC non trouvé dans les en-têtes, utilisation de la première colonne (index 0) comme numero_bc");
                    }
                }
            }
        }
        
        log.info("Final column mapping: {}", map);
        log.info("Format détecté - Colonnes mappées: {}", map.keySet());
        log.info("Colonnes d'achat: quantite_bc={}, prix_achat_unitaire_ht={}, prix_ttc_importe={}", 
            map.get("quantite_bc"), map.get("prix_achat_unitaire_ht"), map.get("prix_ttc_importe"));
        return map;
    }
    
    /**
     * Définit tous les aliases possibles pour chaque colonne
     */
    private Map<String, List<String>> getColumnAliases() {
        Map<String, List<String>> aliases = new HashMap<>();
        
        // DATE BC (optionnel - peut ne pas exister dans la nouvelle structure)
        aliases.put("date_bc", Arrays.asList("date bc", "datebc", "dat bc"));
        
        // N° FAC VTE
        aliases.put("numero_facture_vente", Arrays.asList("n° fac vte", "n fac vte", "numero fac vente", 
                "numero facture vente", "n° facture vente", "facture vente", "fac vte", "fv"));
        
        // DATE FAC VTE / DATE FAC VTF
        aliases.put("date_facture_vente", Arrays.asList("date fac vte", "date fac vtf", "datefac vte", "datefac vtf", 
                "date facture vente", "date facture vte", "date facture vtf"));
        
        // ICE
        aliases.put("ice", Arrays.asList("ice", "i.c.e"));
        
        // N FAC FRS (DOIT être avant "fournisseur" pour éviter les conflits)
        aliases.put("numero_facture_fournisseur", Arrays.asList("n fac frs", "n fac fr", "n° fac frs", 
                "numero fac fournisseur", "numero facture fournisseur", "facture fournisseur", "fac frs",
                "n° fac fr", "n fac fournisseur", "numero fac frs"));
        
        // FACTURE ORIGINE (pour avoirs)
        aliases.put("facture_origine", Arrays.asList("facture origine", "facture origine", "facture annulee",
                "avoir facture", "facture reference", "ref facture", "facture ref"));
        
        // FRS (fournisseur - doit être après numero_facture_fournisseur)
        aliases.put("fournisseur", Arrays.asList("frs", "fr", "fournisseur", "fourni", "supplier", "frs "));
        
        // CLENT
        aliases.put("client", Arrays.asList("clent", "client", "clt"));
        
        // N° BC (première colonne - peut être sans en-tête)
        aliases.put("numero_bc", Arrays.asList("n° bc", "n bc", "numero bc", "numero_bc", "bc", "num bc", "n° bc", "no bc"));
        
        // N° ARTICLE / N° ARTI
        aliases.put("numero_article", Arrays.asList("n° article", "n° arti", "n article", "n arti", 
                "numero article", "numero arti", "numero_article", "article", "n° artic", "n artic", 
                "n° arti cl", "n arti cl"));
        
        // DESIGNATION
        aliases.put("designation", Arrays.asList("designation", "design", "desc", "description", "produit"));
        
        // U
        aliases.put("unite", Arrays.asList("u", "unite", "unit", "unité"));
        
        // QT BC / QT ACHAT
        aliases.put("quantite_bc", Arrays.asList("qt bc", "qt achat", "qtbc", "quantite bc", "quantite_achetee", 
                "quantité achetée", "qte achat", "quantite achat"));
        
        // QT LIVREE AU CLIENT / QT LIVREE CLT (format 2024)
        aliases.put("quantite_livree", Arrays.asList("qt livree", "qt livree clt", "qt livref", "quantite livree", 
                "quantite_vendue", "quantité vendue", "qte vente", "quantite vente",
                "qt livree au client", "qt livree au cl", "quantite livree au client"));
        
        // PRIX ACHAT U HT (format 2024: "PU achat HT" - avec retours à la ligne possibles)
        aliases.put("prix_achat_unitaire_ht", Arrays.asList("prix achat u ht", "prix achat\nu ht", "prix achat u ht", 
                "prix achat unitaire ht", "prix achat unit ht", "pau ht", "pax ht", "pu achat ht", "pu achat u ht"));
        
        // PRIX ACHAT T HT (avec retours à la ligne possibles)
        aliases.put("prix_achat_total_ht", Arrays.asList("prix achat t ht", "prix achat\nt ht", "prix achat t ht", 
                "prix achat total ht", "total achat ht", "tah ht"));
        
        // TX TVA
        aliases.put("taux_tva", Arrays.asList("tx tva", "taux tva", "tva", "taxe", "tva %"));
        
        // FACTURE ACHAT TTC (avec retours à la ligne et espaces en fin possibles)
        aliases.put("facture_achat_ttc", Arrays.asList("facture achat ttc", "facture achat\nttc", "facture achat ttc ", 
                "facture achat\nttc ", "fa ttc", "total achat ttc"));
        
        // PRIX ACHAT TTC (format 2024: "PRIX ACHAT TTC")
        aliases.put("prix_achat_ttc", Arrays.asList("prix achat ttc", "prix achat total ttc", 
                "total achat ttc", "prix achat ttc", "pa ttc"));
        
        // PRIX ACHAT U TTC (avec retours à la ligne possibles)
        aliases.put("prix_achat_unitaire_ttc", Arrays.asList("prix achat u ttc", "prix achat\nu ttc", "prix achat u ttc", 
                "prix achat unitaire ttc", "pau ttc"));
        
        // PRIX DE VENTE U TTC
        aliases.put("prix_vente_unitaire_ttc", Arrays.asList("prix de vente u tt", "prix de vente u ttc", 
                "prix vente unitaire ttc", "pv u ttc", "prix vente u ttc"));
        
        // MARGE U TTC
        aliases.put("marge_unitaire_ttc", Arrays.asList("marge u ttc", "marge unitaire ttc", "marge", "marge ut"));
        
        // PRIX DE VENTE U HT (format 2024: "PU VENTE BF4 HT" - avec retours à la ligne possibles)
        aliases.put("prix_vente_unitaire_ht", Arrays.asList("prix de vente u ht", "prix de vente\nu ht", "prix de vente u ht", 
                "prix vente unitaire ht", "pv u ht", "prix vente u ht", "pu vente bf4 ht", "pu vente ht", "pv bf4 ht"));
        
        // FACTURE VENTE TTC (format 2024: "PT VENTE BF4 TTC", format 2021/2022: "PT VENTE BF4 TTC FORMULE" - avec retours à la ligne possibles)
        aliases.put("facture_vente_ttc", Arrays.asList("facture vente ttc", "facture vente\nt ttc", "facture vente t ttc", 
                "facture vente ttc ", "fv ttc", "total vente ttc", "pt vente bf4 ttc", "pt vente ttc", "pv bf4 ttc",
                "pt vente bf4 ttc formule", "pt vente bf4 ttc formule"));
        
        // PT TTC IMPORTE (format 2021/2022)
        aliases.put("prix_ttc_importe", Arrays.asList("pt ttc importe", "pt ttc import", 
                "prix ttc importe", "total ttc importe"));
        
        // DATE LIVRAISON AU CLIENT (format 2021/2022)
        aliases.put("date_livraison_client", Arrays.asList("date livraison au client", "date livraison client",
                "date livraison", "livraison client"));
        
        // N°BL BF4 (format 2021/2022)
        aliases.put("numero_bl_bf4", Arrays.asList("n bl bf4", "n° bl bf4", "numero bl bf4",
                "no bl bf4", "bl bf4"));
        
        return aliases;
    }
    
    private String normalizeColumnName(String name) {
        if (name == null) return "";
        
        return name
                .toLowerCase()
                .replaceAll("\r\n", " ")     // Remplacer retours à la ligne Windows par espace
                .replaceAll("\n", " ")       // Remplacer retours à la ligne Unix par espace
                .replaceAll("\r", " ")       // Remplacer retours chariot par espace
                .replaceAll("[°'`]", "")     // Retirer caractères spéciaux
                .replaceAll("[éèêë]", "e")   // Normaliser accents e
                .replaceAll("[àâä]", "a")    // Normaliser accents a
                .replaceAll("[îï]", "i")     // Normaliser accents i
                .replaceAll("[ôö]", "o")     // Normaliser accents o
                .replaceAll("[ùûü]", "u")    // Normaliser accents u
                .replaceAll("ç", "c")        // Normaliser ç
                .replaceAll("[^a-z0-9\\s]", "")  // Retirer autres caractères spéciaux
                .replaceAll("\\s+", " ")    // Espaces multiples -> un seul espace
                .trim();                     // Retirer espaces en début/fin
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
    
    /**
     * Classe interne pour stocker les résultats de la détection d'avoir
     */
    private static class AvoirDetectionResult {
        boolean estAvoir = false;
        boolean estAchat = false; // true = avoir achat, false = avoir vente
        String numeroFactureOrigine = null;
        String numeroFacture = null; // Numéro de l'avoir lui-même
    }
    
    /**
     * Détecte si une ligne Excel représente un avoir
     * Stratégies de détection :
     * 1. Mot-clé "AVOIR" dans designation, numero_facture, ou prix
     * 2. Montants négatifs (totalTTC < 0 ou totalHT < 0)
     * 3. Préfixes spéciaux : "AV-", "AVOIR-", "CREDIT-"
     */
    private AvoirDetectionResult detecterAvoir(Row row, Map<String, Integer> columnMap) {
        AvoirDetectionResult result = new AvoirDetectionResult();
        
        // 1. Détection par mot-clé "AVOIR" dans plusieurs colonnes
        String designation = getCellValue(row, columnMap, "designation");
        String numeroFactureAchat = getCellValue(row, columnMap, "numero_facture_fournisseur");
        String numeroFactureVente = getCellValue(row, columnMap, "numero_facture_vente");
        String prixVenteTTCStr = getCellValue(row, columnMap, "prix_vente_unitaire_ttc");
        String prixAchatTTCStr = getCellValue(row, columnMap, "prix_achat_unitaire_ttc");
        
        // Vérifier dans designation
        if (designation != null && designation.trim().toUpperCase().contains("AVOIR")) {
            result.estAvoir = true;
        }
        
        // Vérifier dans numéro facture achat
        if (numeroFactureAchat != null) {
            String numUpper = numeroFactureAchat.trim().toUpperCase();
            if (numUpper.contains("AVOIR") || numUpper.startsWith("AV-") || 
                numUpper.startsWith("AVOIR-") || numUpper.startsWith("CREDIT-")) {
                result.estAvoir = true;
                result.estAchat = true;
                result.numeroFacture = numeroFactureAchat.trim();
            }
        }
        
        // Vérifier dans numéro facture vente
        if (numeroFactureVente != null) {
            String numUpper = numeroFactureVente.trim().toUpperCase();
            if (numUpper.contains("AVOIR") || numUpper.startsWith("AV-") || 
                numUpper.startsWith("AVOIR-") || numUpper.startsWith("CREDIT-")) {
                result.estAvoir = true;
                result.estAchat = false;
                result.numeroFacture = numeroFactureVente.trim();
            }
        }
        
        // Vérifier dans prix
        if (prixVenteTTCStr != null && prixVenteTTCStr.trim().equalsIgnoreCase("AVOIR")) {
            result.estAvoir = true;
            result.estAchat = false;
        }
        if (prixAchatTTCStr != null && prixAchatTTCStr.trim().equalsIgnoreCase("AVOIR")) {
            result.estAvoir = true;
            result.estAchat = true;
        }
        
        // 2. Détection par montants négatifs
        Double totalHT = getDoubleValue(row, columnMap, "facture_achat_ttc");
        if (totalHT == null) {
            totalHT = getDoubleValue(row, columnMap, "facture_vente_ttc");
        }
        if (totalHT != null && totalHT < 0) {
            result.estAvoir = true;
            // Si on a un numéro facture achat, c'est un avoir achat, sinon avoir vente
            if (numeroFactureAchat != null && !numeroFactureAchat.trim().isEmpty()) {
                result.estAchat = true;
                result.numeroFacture = numeroFactureAchat.trim();
            } else if (numeroFactureVente != null && !numeroFactureVente.trim().isEmpty()) {
                result.estAchat = false;
                result.numeroFacture = numeroFactureVente.trim();
            }
        }
        
        // 3. Chercher le numéro de facture d'origine (colonne spéciale ou déduction)
        // Chercher dans les colonnes possibles : "facture_origine", "facture_annulee", etc.
        String factureOrigine = getCellValue(row, columnMap, "facture_origine");
        if (factureOrigine == null) {
            factureOrigine = getCellValue(row, columnMap, "facture_annulee");
        }
        if (factureOrigine == null) {
            factureOrigine = getCellValue(row, columnMap, "avoir_facture");
        }
        result.numeroFactureOrigine = factureOrigine != null ? factureOrigine.trim() : null;
        
        return result;
    }
    
    /**
     * Traite un avoir détecté dans l'import Excel
     */
    private void traiterAvoir(Row row, Map<String, Integer> columnMap,
                             Map<String, BandeCommande> bcMap,
                             Map<String, FactureAchat> faMap,
                             Map<String, FactureVente> fvMap,
                             Map<String, List<LineItem>> faLignesMap,
                             Map<String, List<LineItem>> fvLignesMap,
                             Map<String, String> faToBcNumMap,
                             Map<String, String> fvToBcNumMap,
                             ImportResult result,
                             AvoirDetectionResult detection) {
        
        try {
            if (detection.estAchat) {
                // Traiter avoir achat
                traiterAvoirAchat(row, columnMap, faMap, faLignesMap, faToBcNumMap, result, detection);
            } else {
                // Traiter avoir vente
                traiterAvoirVente(row, columnMap, fvMap, fvLignesMap, fvToBcNumMap, result, detection);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de l'avoir: {}", e.getMessage(), e);
            result.getErrors().add("Erreur traitement avoir: " + e.getMessage());
            result.getWarnings().add("Avoir ignoré à cause d'une erreur: " + e.getMessage());
        }
    }
    
    /**
     * Traite un avoir achat
     */
    private void traiterAvoirAchat(Row row, Map<String, Integer> columnMap,
                                  Map<String, FactureAchat> faMap,
                                  Map<String, List<LineItem>> faLignesMap,
                                  Map<String, String> faToBcNumMap,
                                  ImportResult result,
                                  AvoirDetectionResult detection) {
        
        String numeroBC = getCellValue(row, columnMap, "numero_bc");
        if (numeroBC == null || numeroBC.trim().isEmpty()) {
            result.getWarnings().add("Avoir achat ignoré: numéro BC manquant");
            return;
        }
        final String finalNumeroBC = numeroBC.trim();
        
        String numeroFactureFournisseur = detection.numeroFacture;
        if (numeroFactureFournisseur == null || numeroFactureFournisseur.isEmpty()) {
            numeroFactureFournisseur = getCellValue(row, columnMap, "numero_facture_fournisseur");
        }
        if (numeroFactureFournisseur == null || numeroFactureFournisseur.isEmpty()) {
            numeroFactureFournisseur = "AVOIR-" + System.currentTimeMillis();
            result.getWarnings().add("Avoir achat sans numéro: numéro auto-généré " + numeroFactureFournisseur);
        }
        final String finalNumeroFactureFournisseur = numeroFactureFournisseur.trim();
        
        // Créer ou récupérer la facture avoir
        FactureAchat avoir = faMap.computeIfAbsent(finalNumeroFactureFournisseur, k -> {
            FactureAchat newAvoir = new FactureAchat();
            newAvoir.setNumeroFactureFournisseur(finalNumeroFactureFournisseur);
            newAvoir.setNumeroFactureAchat(finalNumeroFactureFournisseur);
            newAvoir.setEstAvoir(true);
            newAvoir.setTypeFacture("AVOIR");
            newAvoir.setBcReference(finalNumeroBC);
            
            // Date facture (PRIORITÉ 1: DATE FAC VTE - colonne 5, comme pour les factures achat normales)
            LocalDate dateFacture = null;
            
            // PRIORITÉ 1: DATE FAC VTE (colonne 5) - date principale pour les factures achat
            Integer dateFVCol = columnMap.get("date_facture_vente");
            if (dateFVCol != null) {
                Cell dateCell = row.getCell(dateFVCol);
                dateFacture = parseDateFromCell(dateCell);
                if (dateFacture == null) {
                    String dateFV = getCellValue(row, columnMap, "date_facture_vente");
                    dateFacture = parseDate(dateFV);
                }
            }
            
            // PRIORITÉ 2: date_facture_achat (si DATE FAC VTE n'existe pas ou est invalide)
            if (dateFacture == null) {
                Integer dateFACol = columnMap.get("date_facture_achat");
                if (dateFACol != null) {
                    Cell dateCell = row.getCell(dateFACol);
                    dateFacture = parseDateFromCell(dateCell);
                }
            }
            
            // Dernier fallback : date actuelle
            if (dateFacture == null) {
                dateFacture = LocalDate.now(); // Par défaut aujourd'hui
            }
            newAvoir.setDateFacture(dateFacture);
            newAvoir.setDateEcheance(dateFacture.plusMonths(2));
            
            // Fournisseur
            String fournisseurNom = getCellValue(row, columnMap, "fournisseur");
            if (fournisseurNom != null && !fournisseurNom.trim().isEmpty()) {
                String fournisseurId = findOrCreateFournisseur(fournisseurNom, result);
                newAvoir.setFournisseurId(fournisseurId);
            }
            
            newAvoir.setEtatPaiement("non_regle");
            newAvoir.setLignes(new ArrayList<>());
            newAvoir.setPaiements(new ArrayList<>());
            
            return newAvoir;
        });
        
        // Lier à la facture d'origine si trouvée
        if (detection.numeroFactureOrigine != null) {
            Optional<FactureAchat> factureOrigine = factureAchatRepository
                .findByNumeroFactureFournisseur(detection.numeroFactureOrigine);
            
            if (factureOrigine.isPresent()) {
                avoir.setFactureOrigineId(factureOrigine.get().getId());
                avoir.setNumeroFactureOrigine(detection.numeroFactureOrigine);
            } else {
                result.getWarnings().add("Facture d'origine non trouvée pour avoir: " + detection.numeroFactureOrigine);
            }
        }
        
        // Créer la ligne d'avoir (avec montants négatifs)
        LineItem ligne = createLineItemForFacture(row, columnMap, result);
        
        // S'assurer que les montants sont négatifs pour un avoir
        if (ligne.getTotalHT() != null && ligne.getTotalHT() > 0) {
            ligne.setTotalHT(-ligne.getTotalHT());
        }
        if (ligne.getTotalTTC() != null && ligne.getTotalTTC() > 0) {
            ligne.setTotalTTC(-ligne.getTotalTTC());
        }
        if (ligne.getPrixAchatUnitaireHT() != null && ligne.getPrixAchatUnitaireHT() > 0) {
            ligne.setPrixAchatUnitaireHT(-ligne.getPrixAchatUnitaireHT());
        }
        
        // Utiliser finalNumeroBC comme clé pour grouper les lignes
        faLignesMap.computeIfAbsent(finalNumeroBC, k -> new ArrayList<>()).add(ligne);
        faToBcNumMap.put(finalNumeroFactureFournisseur, finalNumeroBC);
        
        result.getWarnings().add("Avoir achat détecté et traité: " + finalNumeroFactureFournisseur);
    }
    
    /**
     * Traite un avoir vente
     */
    private void traiterAvoirVente(Row row, Map<String, Integer> columnMap,
                                  Map<String, FactureVente> fvMap,
                                  Map<String, List<LineItem>> fvLignesMap,
                                  Map<String, String> fvToBcNumMap,
                                  ImportResult result,
                                  AvoirDetectionResult detection) {
        
        String numeroBC = getCellValue(row, columnMap, "numero_bc");
        if (numeroBC == null || numeroBC.trim().isEmpty()) {
            result.getWarnings().add("Avoir vente ignoré: numéro BC manquant");
            return;
        }
        final String finalNumeroBC = numeroBC.trim();
        
        String numeroFactureVente = detection.numeroFacture;
        if (numeroFactureVente == null || numeroFactureVente.isEmpty()) {
            numeroFactureVente = getCellValue(row, columnMap, "numero_facture_vente");
        }
        if (numeroFactureVente == null || numeroFactureVente.isEmpty()) {
            numeroFactureVente = "AVOIR-V-" + System.currentTimeMillis();
            result.getWarnings().add("Avoir vente sans numéro: numéro auto-généré " + numeroFactureVente);
        }
        final String finalNumeroFactureVente = numeroFactureVente.trim();
        
        // Créer ou récupérer la facture avoir
        FactureVente avoir = fvMap.computeIfAbsent(finalNumeroFactureVente, k -> {
            FactureVente newAvoir = new FactureVente();
            newAvoir.setNumeroFactureVente(finalNumeroFactureVente);
            newAvoir.setEstAvoir(true);
            newAvoir.setTypeFacture("AVOIR");
            newAvoir.setBcReference(finalNumeroBC);
            
            // Date facture
            LocalDate dateFacture = null;
            Integer dateCol = columnMap.get("date_facture_vente");
            if (dateCol != null) {
                Cell dateCell = row.getCell(dateCol);
                dateFacture = parseDateFromCell(dateCell);
            }
            if (dateFacture == null) {
                dateFacture = LocalDate.now(); // Par défaut aujourd'hui
            }
            newAvoir.setDateFacture(dateFacture);
            newAvoir.setDateEcheance(dateFacture.plusDays(30));
            
            // Client
            String clientIce = getCellValue(row, columnMap, "ice");
            String clientNom = getCellValue(row, columnMap, "client");
            if (clientNom != null && !clientNom.trim().isEmpty()) {
                String clientId = findOrCreateClient(clientIce, clientNom, result);
                newAvoir.setClientId(clientId);
            }
            
            newAvoir.setEtatPaiement("non_regle");
            newAvoir.setLignes(new ArrayList<>());
            newAvoir.setPaiements(new ArrayList<>());
            
            return newAvoir;
        });
        
        // Lier à la facture d'origine si trouvée
        if (detection.numeroFactureOrigine != null) {
            Optional<FactureVente> factureOrigine = factureVenteRepository
                .findByNumeroFactureVente(detection.numeroFactureOrigine);
            
            if (factureOrigine.isPresent()) {
                avoir.setFactureOrigineId(factureOrigine.get().getId());
                avoir.setNumeroFactureOrigine(detection.numeroFactureOrigine);
            } else {
                result.getWarnings().add("Facture d'origine non trouvée pour avoir: " + detection.numeroFactureOrigine);
            }
        }
        
        // Créer la ligne d'avoir (avec montants négatifs)
        LineItem ligne = createLineItemForFactureVente(row, columnMap, result);
        
        // S'assurer que les montants sont négatifs pour un avoir
        if (ligne.getTotalHT() != null && ligne.getTotalHT() > 0) {
            ligne.setTotalHT(-ligne.getTotalHT());
        }
        if (ligne.getTotalTTC() != null && ligne.getTotalTTC() > 0) {
            ligne.setTotalTTC(-ligne.getTotalTTC());
        }
        if (ligne.getPrixVenteUnitaireHT() != null && ligne.getPrixVenteUnitaireHT() > 0) {
            ligne.setPrixVenteUnitaireHT(-ligne.getPrixVenteUnitaireHT());
        }
        
        fvLignesMap.computeIfAbsent(finalNumeroFactureVente, k -> new ArrayList<>()).add(ligne);
        fvToBcNumMap.put(finalNumeroFactureVente, finalNumeroBC);
        
        result.getWarnings().add("Avoir vente détecté et traité: " + finalNumeroFactureVente);
    }
    
    private void processRow(Row row, Map<String, Integer> columnMap,
                           Map<String, BandeCommande> bcMap,
                           Map<String, FactureAchat> faMap,
                           Map<String, FactureVente> fvMap,
                           Map<String, List<LineItem>> faLignesMap,
                           Map<String, List<LineItem>> fvLignesMap,
                           Map<String, String> faToBcNumMap, // Map temporaire: numeroFA -> numeroBC
                           Map<String, String> fvToBcNumMap, // Map temporaire: numeroFV -> numeroBC
                           Map<String, Map<String, ProductAggregate>> bcProductsMap, // NOUVEAU: Agrégation produits par BC
                           Map<String, List<FactureVente>> fvByBcAndClientMap, // NOUVEAU: Plusieurs FV par client/BC
                           ImportResult result) {
        
        // ========== DÉTECTION DES AVOIRS ==========
        AvoirDetectionResult avoirDetection = detecterAvoir(row, columnMap);
        
        // Si c'est un avoir, le traiter séparément
        if (avoirDetection.estAvoir) {
            traiterAvoir(row, columnMap, bcMap, faMap, fvMap, faLignesMap, fvLignesMap, 
                        faToBcNumMap, fvToBcNumMap, result, avoirDetection);
            return;
        }
        
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
            
            // Parser la date BC - utiliser DATE FAC VTE comme fallback si DATE BC n'existe pas
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
            
            // Si DATE BC n'existe pas, utiliser DATE FAC VTE comme fallback
            if (dateBC == null) {
                Integer dateFVCol = columnMap.get("date_facture_vente");
                if (dateFVCol != null) {
                    Cell dateCell = row.getCell(dateFVCol);
                    dateBC = parseDateFromCell(dateCell);
                    if (dateBC == null) {
                        String dateFV = getCellValue(row, columnMap, "date_facture_vente");
                        dateBC = parseDate(dateFV);
                    }
                }
            }
            
            newBc.setDateBC(dateBC);
            
            newBc.setClientId(finalClientId);
            newBc.setFournisseurId(finalFournisseurId);
            newBc.setEtat("envoyee"); // Si factures présentes, la BC est déjà envoyée
            newBc.setLignes(new ArrayList<>());
            return newBc;
        });
        
        // 5. NOUVELLE LOGIQUE : Agréger les données produit au lieu d'ajouter directement
        // Récupérer les données du produit depuis la ligne Excel
        String produitRef = getCellValue(row, columnMap, "numero_article");
        String designation = getCellValue(row, columnMap, "designation");
        String unite = getCellValue(row, columnMap, "unite");
        
        // Validation: ignorer les lignes sans données significatives
        if ((designation == null || designation.trim().isEmpty()) && 
            (produitRef == null || produitRef.trim().isEmpty())) {
            result.getWarnings().add("Ligne ignorée pour BC " + numeroBC + ": pas de désignation ni référence produit");
            return;
        }
        
        // Normaliser les valeurs et créer des copies finales pour les lambdas
        final String finalProduitRef = produitRef != null ? produitRef.trim() : null;
        final String finalDesignation = designation != null ? designation.trim() : null;
        final String finalUnite = (unite == null || unite.trim().isEmpty()) ? "U" : unite.trim();
        
        // Créer la clé produit unique : refArticle + "|" + designation + "|" + unite
        String produitKey = (finalProduitRef != null ? finalProduitRef : "") + "|" + 
                           (finalDesignation != null ? finalDesignation : "") + "|" + finalUnite;
        
        // Récupérer ou créer l'agrégat pour ce produit dans cette BC
        Map<String, ProductAggregate> productsForBC = bcProductsMap.computeIfAbsent(finalNumeroBC, k -> new HashMap<>());
        ProductAggregate productAgg = productsForBC.computeIfAbsent(produitKey, k -> {
            ProductAggregate agg = new ProductAggregate();
            agg.produitRef = finalProduitRef;
            agg.designation = finalDesignation;
            agg.unite = finalUnite;
            return agg;
        });
        
        // Récupérer les quantités et prix
        Double qteBC = getDoubleValue(row, columnMap, "quantite_bc");
        if (qteBC == null) {
            qteBC = getDoubleValue(row, columnMap, "quantite_livree");
        }
        Double prixAchatHT = getDoubleValue(row, columnMap, "prix_achat_unitaire_ht");
        
        // Fallback pour format 2021/2022: calculer depuis PT TTC IMPORTE si prix_achat_unitaire_ht n'existe pas
        if (prixAchatHT == null) {
            Double ptTTCImporte = getDoubleValue(row, columnMap, "prix_ttc_importe");
            Double tva = getPercentageValue(row, columnMap, "taux_tva");
            if (tva == null) tva = 20.0;
            
            if (ptTTCImporte != null && ptTTCImporte > 0 && qteBC != null && qteBC > 0) {
                Double prixUnitaireTTC = ptTTCImporte / qteBC;
                prixAchatHT = prixUnitaireTTC / (1 + (tva / 100));
            }
        }
        
        // Récupérer TVA
        Double tva = getPercentageValue(row, columnMap, "taux_tva");
        if (tva == null) tva = 20.0;
        productAgg.tva = tva;
        
        // Ajouter les données d'achat à l'agrégat
        if (qteBC != null && qteBC > 0 && prixAchatHT != null && prixAchatHT > 0) {
            productAgg.addAchat(qteBC, prixAchatHT);
        }
        
        // Récupérer les données de vente
        Double qteLivree = getDoubleValue(row, columnMap, "quantite_livree");
        Double prixVenteHT = getDoubleValue(row, columnMap, "prix_vente_unitaire_ht");
        if (prixVenteHT == null || prixVenteHT == 0) {
            // Calculer depuis prix vente TTC si disponible
            Double prixVenteTTC = getDoubleValue(row, columnMap, "prix_vente_unitaire_ttc");
            Double tauxTVA = getPercentageValue(row, columnMap, "taux_tva");
            if (prixVenteTTC != null && prixVenteTTC > 0 && tauxTVA != null) {
                prixVenteHT = prixVenteTTC / (1 + (tauxTVA / 100));
            }
        }
        
        // Créer aussi une LineItem pour la rétrocompatibilité (sera convertie plus tard)
        LineItem ligne = createLineItem(row, columnMap, result);
        bc.getLignes().add(ligne);
        
        // 6. Créer ou récupérer la facture achat
        // On crée toujours une facture achat pour chaque BC, même sans numéro facture fournisseur
        // Le numéro facture fournisseur est stocké comme référence externe
        String numeroFactureFournisseur = getCellValue(row, columnMap, "numero_facture_fournisseur");
        if (numeroFactureFournisseur != null) {
            numeroFactureFournisseur = numeroFactureFournisseur.trim();
        }
        
        // Utiliser la BC comme clé pour grouper les factures achats (une facture achat par BC)
        // Si plusieurs lignes ont la même BC, elles seront regroupées dans la même facture
        final String finalNumeroFactureFournisseur = numeroFactureFournisseur; // Référence fournisseur
        
        FactureAchat fa = faMap.computeIfAbsent(finalNumeroBC, k -> {
            FactureAchat newFa = new FactureAchat();
            // Initialiser les champs avoir par défaut (rétrocompatibilité)
            newFa.setEstAvoir(false);
            newFa.setTypeFacture("NORMALE");
            
            // Utiliser "N FAC FRS" comme numéro de facture achat (notre référence interne)
            if (finalNumeroFactureFournisseur != null && !finalNumeroFactureFournisseur.isEmpty()) {
                newFa.setNumeroFactureAchat(finalNumeroFactureFournisseur);
                newFa.setNumeroFactureFournisseur(finalNumeroFactureFournisseur);
            }
            
            // Date facture achat (PRIORITÉ 1: DATE FAC VTE - colonne 5, comme demandé pour l'historique de trésorerie)
            LocalDate dateFacture = null;
            
            // PRIORITÉ 1: DATE FAC VTE (colonne 5) - date principale pour les factures achat
            Integer dateFVCol = columnMap.get("date_facture_vente");
            if (dateFVCol != null) {
                Cell dateCell = row.getCell(dateFVCol);
                dateFacture = parseDateFromCell(dateCell);
                if (dateFacture == null) {
                    String dateFV = getCellValue(row, columnMap, "date_facture_vente");
                    dateFacture = parseDate(dateFV);
                }
            }
            
            // PRIORITÉ 2: date_facture_achat (si DATE FAC VTE n'existe pas ou est invalide)
            if (dateFacture == null) {
                Integer dateFACol = columnMap.get("date_facture_achat");
                if (dateFACol != null) {
                    Cell dateCell = row.getCell(dateFACol);
                    dateFacture = parseDateFromCell(dateCell);
                }
            }
            
            // PRIORITÉ 3: Fallback sur date BC si toujours pas de date
            if (dateFacture == null) {
                Integer dateBCCol = columnMap.get("date_bc");
                if (dateBCCol != null) {
                    Cell dateCell = row.getCell(dateBCCol);
                    dateFacture = parseDateFromCell(dateCell);
                    if (dateFacture == null) {
                        String dateBC = getCellValue(row, columnMap, "date_bc");
                        dateFacture = parseDate(dateBC);
                    }
                }
            }
            
            // Dernier fallback : date actuelle avec warning
            if (dateFacture == null) {
                dateFacture = LocalDate.now();
                result.getWarnings().add("Date facture achat manquante pour BC " + finalNumeroBC + ", utilisation de la date actuelle");
            }
            
            newFa.setDateFacture(dateFacture);
            
            // Date échéance = date facture + 2 mois (règle métier)
            if (newFa.getDateFacture() != null) {
                newFa.setDateEcheance(newFa.getDateFacture().plusMonths(2));
            }
            
            newFa.setBcReference(finalNumeroBC);
            newFa.setFournisseurId(finalFournisseurId);
            newFa.setEtatPaiement("non_regle");
            newFa.setLignes(new ArrayList<>());
            newFa.setPaiements(new ArrayList<>());
            
            return newFa;
        });
        
        // Si le numéro facture fournisseur est présent et n'est pas encore défini, le mettre à jour
        // Utiliser "N FAC FRS" comme numéro de facture achat (notre référence interne)
        if (finalNumeroFactureFournisseur != null && !finalNumeroFactureFournisseur.isEmpty()) {
            if (fa.getNumeroFactureFournisseur() == null || fa.getNumeroFactureFournisseur().isEmpty()) {
                fa.setNumeroFactureFournisseur(finalNumeroFactureFournisseur);
            }
            // Mettre à jour le numéro facture achat si non défini
            if (fa.getNumeroFactureAchat() == null || fa.getNumeroFactureAchat().isEmpty()) {
                fa.setNumeroFactureAchat(finalNumeroFactureFournisseur);
            }
        }
        
        // Stocker l'association FA -> BC (utiliser BC comme clé)
        faToBcNumMap.put(finalNumeroBC, finalNumeroBC);
        
        // Ajouter la ligne à la facture achat
        LineItem faLigne = createLineItemForFacture(row, columnMap, result);
        faLignesMap.computeIfAbsent(finalNumeroBC, k -> new ArrayList<>()).add(faLigne);
        
        // 7. NOUVELLE LOGIQUE : Créer ou récupérer la facture vente (plusieurs FV possibles par client/BC)
        String numeroFV = getCellValue(row, columnMap, "numero_facture_vente");
        
        // Ajouter les données de vente à l'agrégat (si facture vente présente)
        if (numeroFV != null && !numeroFV.trim().isEmpty() && 
            qteLivree != null && qteLivree > 0 && prixVenteHT != null && prixVenteHT > 0) {
            productAgg.addVente(finalClientId, numeroFV.trim(), qteLivree, prixVenteHT);
        }
        
        if (numeroFV != null && !numeroFV.trim().isEmpty()) {
            numeroFV = numeroFV.trim();
            final String finalNumeroFV = numeroFV;
            
            // Clé pour grouper les FV par BC et client
            String bcClientKey = finalNumeroBC + "|" + finalClientId;
            
            // Récupérer ou créer la liste des FV pour ce BC+Client
            List<FactureVente> fvList = fvByBcAndClientMap.computeIfAbsent(bcClientKey, k -> new ArrayList<>());
            
            // Chercher si cette FV existe déjà dans la liste
            FactureVente fv = fvList.stream()
                .filter(f -> finalNumeroFV.equals(f.getNumeroFactureVente()))
                .findFirst()
                .orElse(null);
            
            // Si la FV n'existe pas, la créer
            if (fv == null) {
                fv = new FactureVente();
                fv.setNumeroFactureVente(finalNumeroFV);
                // Initialiser les champs avoir par défaut (rétrocompatibilité)
                fv.setEstAvoir(false);
                fv.setTypeFacture("NORMALE");
                
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
                fv.setDateFacture(dateFacture);
                
                // Date échéance = date facture + 30 jours (défaut)
                if (dateFacture != null) {
                    fv.setDateEcheance(dateFacture.plusDays(30));
                }
                
                // Définir la référence BC directement sur la facture
                fv.setBcReference(finalNumeroBC);
                
                fv.setClientId(finalClientId);
                fv.setEtatPaiement("non_regle");
                fv.setLignes(new ArrayList<>());
                fv.setPaiements(new ArrayList<>());
                
                // Ajouter à la liste et à la map globale
                fvList.add(fv);
                fvMap.put(finalNumeroFV, fv); // Garder aussi dans fvMap pour compatibilité
            }
            
            // Stocker l'association FV -> BC
            fvToBcNumMap.put(finalNumeroFV, finalNumeroBC);
            
            // Ajouter la ligne à la facture vente
            LineItem fvLigne = createLineItemForFactureVente(row, columnMap, result);
            fvLignesMap.computeIfAbsent(finalNumeroFV, k -> new ArrayList<>()).add(fvLigne);
        }
    }
    
    /**
     * Construit la référence article en utilisant uniquement la désignation.
     * Ignore le produitRef s'il ressemble à un numéro de ligne de BC (simple nombre).
     * 
     * @param produitRef Le numéro de l'article (peut être null ou vide, peut être un numéro de ligne)
     * @param designation Le nom de l'article (peut être null ou vide)
     * @return La référence (uniquement la designation, ou produitRef si c'est une vraie référence)
     */
    private String buildRefArticle(String produitRef, String designation) {
        String desig = designation != null ? designation.trim() : "";
        
        // Si produitRef existe et n'est PAS un simple numéro (numéro de ligne), l'utiliser
        // Sinon, utiliser uniquement la designation
        if (produitRef != null && !produitRef.trim().isEmpty()) {
            String ref = produitRef.trim();
            // Vérifier si c'est un simple numéro (numéro de ligne de BC)
            // Un numéro de ligne est généralement un nombre seul (ex: "2", "11", "123")
            if (!ref.matches("^\\d+$")) {
                // Ce n'est pas un numéro de ligne, c'est une vraie référence produit
                if (!desig.isEmpty()) {
                    return ref + " - " + desig;
                } else {
                    return ref;
                }
            }
        }
        
        // Utiliser uniquement la designation comme référence
        return desig.isEmpty() ? "" : desig;
    }
    
    /**
     * Nettoie la référence produit en supprimant les préfixes numériques (numéros de ligne BC)
     * @param ref La référence à nettoyer
     * @return La référence nettoyée
     */
    private String cleanProductRef(String ref) {
        if (ref == null || ref.trim().isEmpty()) {
            return "";
        }
        String cleaned = ref.trim();
        // Supprimer les préfixes numériques suivis de " - " ou " -"
        // Ex: "2 - CABLE 1.5" -> "CABLE 1.5"
        // Ex: "11 - CABLE 1.5" -> "CABLE 1.5"
        cleaned = cleaned.replaceFirst("^\\d+\\s*-\\s*", "");
        return cleaned;
    }
    
    /**
     * Crée ou met à jour un produit à partir d'un ProductAggregate.
     * Calcule les prix pondérés et met à jour le produit dans la base de données.
     */
    private void createOrUpdateProductFromAggregate(ProductAggregate agg, String fournisseurId, ImportResult result) {
        try {
            // Calculer les prix pondérés depuis cet agrégat
            Double prixAchatPondere = agg.getPrixAchatPondere();
            Double prixVentePondere = agg.getPrixVentePondere();
            
            // Construire la référence article combinée (numéro + nom)
            String refArticle = buildRefArticle(agg.produitRef, agg.designation);
            
            // Chercher produit existant par ref + designation + unite
            // On essaie d'abord avec le nouveau format (refArticle combiné), puis avec l'ancien format (juste le numéro)
            // pour gérer la transition des produits existants
            Optional<Product> existingOpt = productRepository.findByRefArticleAndDesignationAndUnite(
                refArticle,
                agg.designation != null ? agg.designation : "",
                agg.unite != null ? agg.unite : "U"
            );
            
            // Si pas trouvé avec le nouveau format, essayer avec l'ancien format (juste le numéro)
            if (!existingOpt.isPresent() && agg.produitRef != null && !agg.produitRef.trim().isEmpty()) {
                existingOpt = productRepository.findByRefArticleAndDesignationAndUnite(
                    agg.produitRef.trim(),
                    agg.designation != null ? agg.designation : "",
                    agg.unite != null ? agg.unite : "U"
                );
            }
            
            Product product;
            if (existingOpt.isPresent()) {
                // Mettre à jour le produit existant
                product = existingOpt.get();
                
                // Mettre à jour les prix pondérés
                // Note: Pour un calcul global depuis toutes les BC, il faudra utiliser ProductPriceService
                // Pour l'instant, on met à jour avec les prix de cette BC
                if (prixAchatPondere != null) {
                    product.setPrixAchatPondereHT(prixAchatPondere);
                }
                if (prixVentePondere != null) {
                    product.setPrixVentePondereHT(prixVentePondere);
                }
                
                // Mettre à jour les autres champs si nécessaire
                // Mettre à jour refArticle avec la nouvelle valeur combinée
                product.setRefArticle(refArticle);
                if (agg.designation != null && !agg.designation.isEmpty()) {
                    product.setDesignation(agg.designation);
                }
                if (agg.unite != null && !agg.unite.isEmpty()) {
                    product.setUnite(agg.unite);
                }
                if (agg.tva != null) {
                    product.setTva(agg.tva);
                }
                if (fournisseurId != null) {
                    product.setFournisseurId(fournisseurId);
                }
                
                product.setDerniereMiseAJourPrix(LocalDateTime.now());
                product.setUpdatedAt(LocalDateTime.now());
                
                product = productRepository.save(product);
                log.debug("Produit mis à jour: {} - Prix achat pondéré: {}, Prix vente pondéré: {}", 
                    product.getRefArticle(), prixAchatPondere, prixVentePondere);
            } else {
                // Créer nouveau produit
                product = Product.builder()
                    .refArticle(refArticle)
                    .designation(agg.designation)
                    .unite(agg.unite != null ? agg.unite : "U")
                    .prixAchatPondereHT(prixAchatPondere)
                    .prixVentePondereHT(prixVentePondere)
                    .tva(agg.tva)
                    .fournisseurId(fournisseurId)
                    .quantiteEnStock(0)
                    .derniereMiseAJourPrix(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                
                product = productRepository.save(product);
                log.debug("Nouveau produit créé: {} - Prix achat pondéré: {}, Prix vente pondéré: {}", 
                    product.getRefArticle(), prixAchatPondere, prixVentePondere);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la création/mise à jour du produit {}: {}", 
                agg.produitRef != null ? agg.produitRef : "inconnu", e.getMessage(), e);
            result.getWarnings().add("Erreur produit " + (agg.produitRef != null ? agg.produitRef : "inconnu") + 
                ": " + e.getMessage());
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
        // Fallback pour format 2021/2022: utiliser quantite_livree si quantite_bc n'existe pas
        if (qteBC == null) {
            qteBC = getDoubleValue(row, columnMap, "quantite_livree");
        }
        if (qteBC == null) {
            // Essayer aussi "qt achat" directement
            String qteAchatStr = getCellValue(row, columnMap, "quantite_bc");
            if (qteAchatStr != null && !qteAchatStr.trim().isEmpty()) {
                log.debug("Trying to parse quantite_bc as string: {}", qteAchatStr);
            }
        }
        ligne.setQuantiteAchetee(qteBC != null ? qteBC.intValue() : 0);
        
        Double qteLivree = getDoubleValue(row, columnMap, "quantite_livree");
        ligne.setQuantiteVendue(qteLivree != null ? qteLivree.intValue() : ligne.getQuantiteAchetee());
        
        // Prix d'achat unitaire HT
        Double prixAchatHT = getDoubleValue(row, columnMap, "prix_achat_unitaire_ht");
        
        // Fallback pour format 2021/2022: calculer depuis PT TTC IMPORTE si prix_achat_unitaire_ht n'existe pas
        if (prixAchatHT == null) {
            Double ptTTCImporte = getDoubleValue(row, columnMap, "prix_ttc_importe");
            Double tva = getPercentageValue(row, columnMap, "taux_tva");
            if (tva == null) tva = 20.0;
            
            if (ptTTCImporte != null && ptTTCImporte > 0) {
                Integer qte = ligne.getQuantiteAchetee();
                if (qte != null && qte > 0) {
                    // Prix unitaire TTC = PT TTC / Quantite
                    Double prixUnitaireTTC = ptTTCImporte / qte;
                    // Prix unitaire HT = Prix TTC / (1 + TVA/100)
                    prixAchatHT = prixUnitaireTTC / (1 + (tva / 100));
                }
            }
        }
        
        ligne.setPrixAchatUnitaireHT(prixAchatHT);
        
        // Log pour debug si prix manquant
        if (prixAchatHT == null && ligne.getDesignation() != null) {
            log.debug("Row with designation '{}': prixAchatHT is null", ligne.getDesignation());
        }
        
        // Prix de vente unitaire HT
        Double prixVenteHT = getDoubleValue(row, columnMap, "prix_vente_unitaire_ht");
        if (prixVenteHT == null || prixVenteHT == 0) {
            // Calculer depuis prix vente TTC si disponible
            Double prixVenteTTC = getDoubleValue(row, columnMap, "prix_vente_unitaire_ttc");
            Double tauxTVA = getPercentageValue(row, columnMap, "taux_tva");
            if (prixVenteTTC != null && prixVenteTTC > 0 && tauxTVA != null) {
                prixVenteHT = prixVenteTTC / (1 + (tauxTVA / 100));
            }
        }
        ligne.setPrixVenteUnitaireHT(prixVenteHT != null ? prixVenteHT : 0.0);
        
        // TVA (gérer le format pourcentage "20%" -> 20.0)
        Double tva = getPercentageValue(row, columnMap, "taux_tva");
        ligne.setTva(tva != null ? tva : 20.0); // Défaut 20%
        
        // Calculer les totaux
        calculateLineItemTotals(ligne);
        
        // Log pour debug si totaux à 0
        if (ligne.getTotalHT() == null || ligne.getTotalHT() == 0) {
            log.debug("LineItem totals are 0: qte={}, prixHT={}, totalHT={}", 
                    ligne.getQuantiteAchetee(), ligne.getPrixAchatUnitaireHT(), ligne.getTotalHT());
        }
        
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
        // Total HT achat (arrondi)
        if (ligne.getQuantiteAchetee() != null && ligne.getPrixAchatUnitaireHT() != null) {
            ligne.setTotalHT(NumberUtils.roundTo2Decimals(ligne.getQuantiteAchetee() * ligne.getPrixAchatUnitaireHT()));
        }
        
        // Total TTC achat (arrondi)
        if (ligne.getTotalHT() != null && ligne.getTva() != null) {
            ligne.setTotalTTC(NumberUtils.roundTo2Decimals(ligne.getTotalHT() * (1 + (ligne.getTva() / 100))));
        }
        
        // Marge unitaire (arrondie)
        if (ligne.getPrixVenteUnitaireHT() != null && ligne.getPrixAchatUnitaireHT() != null && ligne.getPrixAchatUnitaireHT() > 0) {
            ligne.setMargeUnitaire(NumberUtils.roundTo2Decimals(ligne.getPrixVenteUnitaireHT() - ligne.getPrixAchatUnitaireHT()));
            ligne.setMargePourcentage(NumberUtils.roundTo2Decimals((ligne.getMargeUnitaire() / ligne.getPrixAchatUnitaireHT()) * 100));
        }
    }
    
    /**
     * Convertit les lignes (ancienne structure) vers lignesAchat (nouvelle structure)
     * pour assurer la compatibilité avec le nouveau modèle
     */
    private void convertLignesToLignesAchat(BandeCommande bc) {
        // Si lignesAchat existe déjà, ne rien faire
        if (bc.getLignesAchat() != null && !bc.getLignesAchat().isEmpty()) {
            return;
        }
        
        // Si pas de lignes, initialiser une liste vide
        if (bc.getLignes() == null || bc.getLignes().isEmpty()) {
            bc.setLignesAchat(new ArrayList<>());
            return;
        }
        
        // Convertir chaque LineItem en LigneAchat
        List<LigneAchat> lignesAchat = new ArrayList<>();
        for (LineItem ligne : bc.getLignes()) {
            LigneAchat ligneAchat = LigneAchat.builder()
                    .produitRef(ligne.getProduitRef())
                    .designation(ligne.getDesignation())
                    .unite(ligne.getUnite() != null ? ligne.getUnite() : "U")
                    .quantiteAchetee(ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee().doubleValue() : 0.0)
                    .prixAchatUnitaireHT(ligne.getPrixAchatUnitaireHT())
                    .tva(ligne.getTva())
                    .build();
            
            // Calculer les totaux pour cette ligne (arrondis)
            if (ligneAchat.getQuantiteAchetee() != null && ligneAchat.getPrixAchatUnitaireHT() != null) {
                ligneAchat.setTotalHT(NumberUtils.roundTo2Decimals(ligneAchat.getQuantiteAchetee() * ligneAchat.getPrixAchatUnitaireHT()));
                if (ligneAchat.getTva() != null) {
                    ligneAchat.setTotalTTC(NumberUtils.roundTo2Decimals(ligneAchat.getTotalHT() * (1 + (ligneAchat.getTva() / 100.0))));
                }
            }
            
            lignesAchat.add(ligneAchat);
        }
        
        bc.setLignesAchat(lignesAchat);
    }
    
    private void calculateBCTotals(BandeCommande bc) {
        if (bc.getLignes() == null || bc.getLignes().isEmpty()) {
            log.warn("BC {} has no lines, totals will be 0", bc.getNumeroBC());
            return;
        }
        
        double totalAchatHT = 0;
        double totalAchatTTC = 0;
        double totalVenteHT = 0;
        double totalVenteTTC = 0;
        
        int lignesAvecDonnees = 0;
        for (LineItem ligne : bc.getLignes()) {
            if (ligne.getPrixAchatUnitaireHT() != null && ligne.getQuantiteAchetee() != null && 
                ligne.getPrixAchatUnitaireHT() > 0 && ligne.getQuantiteAchetee() > 0) {
                double ht = ligne.getPrixAchatUnitaireHT() * ligne.getQuantiteAchetee();
                totalAchatHT += ht;
                if (ligne.getTva() != null) {
                    totalAchatTTC += ht * (1 + (ligne.getTva() / 100));
                }
                lignesAvecDonnees++;
            }
            
            if (ligne.getPrixVenteUnitaireHT() != null && ligne.getQuantiteVendue() != null &&
                ligne.getPrixVenteUnitaireHT() > 0 && ligne.getQuantiteVendue() > 0) {
                double ht = ligne.getPrixVenteUnitaireHT() * ligne.getQuantiteVendue();
                totalVenteHT += ht;
                if (ligne.getTva() != null) {
                    totalVenteTTC += ht * (1 + (ligne.getTva() / 100));
                }
            }
        }
        
        bc.setTotalAchatHT(NumberUtils.roundTo2Decimals(totalAchatHT));
        bc.setTotalAchatTTC(NumberUtils.roundTo2Decimals(totalAchatTTC));
        bc.setTotalVenteHT(NumberUtils.roundTo2Decimals(totalVenteHT));
        bc.setTotalVenteTTC(NumberUtils.roundTo2Decimals(totalVenteTTC));
        bc.setMargeTotale(NumberUtils.roundTo2Decimals(totalVenteHT - totalAchatHT));
        
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(NumberUtils.roundTo2Decimals(((totalVenteHT - totalAchatHT) / totalAchatHT) * 100));
        }
        
        // Log pour debug si totaux à 0
        if (totalAchatHT == 0 && totalVenteHT == 0) {
            log.warn("BC {} has totals at 0: {} lines processed, {} lines with data", 
                    bc.getNumeroBC(), bc.getLignes().size(), lignesAvecDonnees);
        } else {
            log.debug("BC {} totals: AchatHT={}, VenteHT={}, Marge={}%", 
                    bc.getNumeroBC(), totalAchatHT, totalVenteHT, bc.getMargePourcentage());
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
                totalHT += ligne.getTotalHT(); // Peut être négatif pour avoir
            }
            if (ligne.getTotalTTC() != null && ligne.getTotalHT() != null) {
                totalTVA += NumberUtils.roundTo2Decimals(ligne.getTotalTTC() - ligne.getTotalHT());
                totalTTC += ligne.getTotalTTC(); // Peut être négatif pour avoir
            }
        }
        
        fa.setTotalHT(NumberUtils.roundTo2Decimals(totalHT));
        fa.setTotalTVA(NumberUtils.roundTo2Decimals(totalTVA));
        fa.setTotalTTC(NumberUtils.roundTo2Decimals(totalTTC));
        
        // Pour les avoirs, le montant restant est aussi négatif (ou positif si partiellement utilisé)
        fa.setMontantRestant(NumberUtils.roundTo2Decimals(totalTTC)); // Peut être négatif pour avoir
        
        // S'assurer que si c'est un avoir, les montants sont bien négatifs
        if (Boolean.TRUE.equals(fa.getEstAvoir())) {
            if (fa.getTotalHT() != null && fa.getTotalHT() > 0) {
                fa.setTotalHT(-fa.getTotalHT());
                log.warn("Avoir achat {}: montant HT positif, inversion appliquée", fa.getNumeroFactureAchat());
            }
            if (fa.getTotalTTC() != null && fa.getTotalTTC() > 0) {
                fa.setTotalTTC(-fa.getTotalTTC());
                fa.setMontantRestant(fa.getTotalTTC());
                log.warn("Avoir achat {}: montant TTC positif, inversion appliquée", fa.getNumeroFactureAchat());
            }
        }
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
            // Pour avoir, prix et quantités peuvent être négatifs
            if (ligne.getPrixVenteUnitaireHT() != null && ligne.getQuantiteVendue() != null) {
                double ht = NumberUtils.roundTo2Decimals(ligne.getPrixVenteUnitaireHT() * ligne.getQuantiteVendue());
                totalHT += ht; // Peut être négatif pour avoir
                if (ligne.getTva() != null) {
                    double ttc = NumberUtils.roundTo2Decimals(ht * (1 + (ligne.getTva() / 100)));
                    totalTVA += NumberUtils.roundTo2Decimals(ttc - ht);
                    totalTTC += ttc; // Peut être négatif pour avoir
                }
            }
            
            // Si les totaux sont déjà calculés (ligne d'avoir importée), les utiliser
            if (ligne.getTotalHT() != null) {
                totalHT = ligne.getTotalHT();
            }
            if (ligne.getTotalTTC() != null) {
                totalTTC = ligne.getTotalTTC();
                if (ligne.getTotalHT() != null) {
                    totalTVA = NumberUtils.roundTo2Decimals(totalTTC - totalHT);
                }
            }
        }
        
        fv.setTotalHT(NumberUtils.roundTo2Decimals(totalHT));
        fv.setTotalTVA(NumberUtils.roundTo2Decimals(totalTVA));
        fv.setTotalTTC(NumberUtils.roundTo2Decimals(totalTTC));
        
        // Pour les avoirs, le montant restant est aussi négatif
        fv.setMontantRestant(NumberUtils.roundTo2Decimals(totalTTC)); // Peut être négatif pour avoir
        
        // S'assurer que si c'est un avoir, les montants sont bien négatifs
        if (Boolean.TRUE.equals(fv.getEstAvoir())) {
            if (fv.getTotalHT() != null && fv.getTotalHT() > 0) {
                fv.setTotalHT(-fv.getTotalHT());
                log.warn("Avoir vente {}: montant HT positif, inversion appliquée", fv.getNumeroFactureVente());
            }
            if (fv.getTotalTTC() != null && fv.getTotalTTC() > 0) {
                fv.setTotalTTC(-fv.getTotalTTC());
                fv.setMontantRestant(fv.getTotalTTC());
                log.warn("Avoir vente {}: montant TTC positif, inversion appliquée", fv.getNumeroFactureVente());
            }
        }
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
            Optional<Client> existing = clientRepository.findByNomIgnoreCase(nom.trim());
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
            Optional<Supplier> existing = supplierRepository.findByNom(nom.trim());
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
                    // Ignorer les valeurs non numériques comme "AVOIR"
                    if (strValue.equalsIgnoreCase("AVOIR") || 
                        strValue.equalsIgnoreCase("avoir") ||
                        !strValue.matches(".*\\d+.*")) {
                        return null;
                    }
                    // Gérer format français avec virgule et espaces (ex: "85 750,00")
                    // Retirer tous les espaces (séparateurs de milliers)
                    strValue = strValue.replaceAll("\\s+", "").trim();
                    
                    // Si la virgule est présente, c'est un format français
                    if (strValue.contains(",")) {
                        // Remplacer la virgule par un point pour le parsing
                        strValue = strValue.replace(",", ".");
                        try {
                            return Double.parseDouble(strValue);
                        } catch (NumberFormatException e) {
                            // Essayer avec le format français (virgule comme séparateur décimal)
                            try {
                                return FRENCH_NUMBER_FORMAT.parse(strValue.replace(".", ",")).doubleValue();
                            } catch (ParseException e2) {
                                log.warn("Cannot parse French number: {}", strValue);
                                return null;
                            }
                        }
                    } else {
                        // Format standard avec point
                        try {
                            return Double.parseDouble(strValue);
                        } catch (NumberFormatException e) {
                            log.warn("Cannot parse number: {}", strValue);
                            return null;
                        }
                    }
                case FORMULA:
                    try {
                        // Vérifier si la formule retourne une valeur numérique
                        if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                            return cell.getNumericCellValue();
                        } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                            // Essayer de parser depuis la string
                            String formulaStrValue = cell.getStringCellValue().trim();
                            if (formulaStrValue.isEmpty()) return null;
                            // Ignorer les valeurs non numériques comme "AVOIR"
                            if (formulaStrValue.equalsIgnoreCase("AVOIR") || 
                                !formulaStrValue.matches(".*\\d+.*")) {
                                return null;
                            }
                            // Retirer tous les espaces et gérer format français
                            formulaStrValue = formulaStrValue.replaceAll("\\s+", "").trim();
                            if (formulaStrValue.contains(",")) {
                                formulaStrValue = formulaStrValue.replace(",", ".");
                            }
                            try {
                                return Double.parseDouble(formulaStrValue);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }
                        return null;
                    } catch (Exception e) {
                        log.warn("Error getting numeric value from formula cell: {}", e.getMessage());
                        return null;
                    }
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("Error parsing double from cell: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse une valeur de pourcentage depuis une cellule Excel
     * Gère les formats "20%" -> 20.0, "0.2" -> 20.0 (si < 1), "20" -> 20.0
     */
    private Double getPercentageValue(Row row, Map<String, Integer> columnMap, String columnName) {
        Integer colIndex = columnMap.get(columnName);
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    double numValue = cell.getNumericCellValue();
                    // Si la valeur est < 1, c'est probablement un décimal (0.2 = 20%), multiplier par 100
                    if (numValue < 1 && numValue > 0) {
                        return numValue * 100;
                    }
                    return numValue;
                case STRING:
                    String strValue = cell.getStringCellValue().trim();
                    if (strValue.isEmpty()) return null;
                    
                    // Retirer le signe % s'il existe
                    boolean hasPercent = strValue.endsWith("%");
                    if (hasPercent) {
                        strValue = strValue.substring(0, strValue.length() - 1).trim();
                    }
                    
                    // Ignorer les valeurs non numériques
                    if (!strValue.matches(".*\\d+.*")) {
                        return null;
                    }
                    
                    // Gérer format français avec virgule
                    strValue = strValue.replace(" ", "").replace(",", ".");
                    try {
                        double value = Double.parseDouble(strValue);
                        // Si la valeur est < 1 et qu'il n'y avait pas de %, c'est probablement un décimal
                        if (!hasPercent && value < 1 && value > 0) {
                            return value * 100;
                        }
                        return value;
                    } catch (NumberFormatException e) {
                        // Essayer avec le format français
                        try {
                            double value = FRENCH_NUMBER_FORMAT.parse(strValue.replace(".", ",")).doubleValue();
                            if (!hasPercent && value < 1 && value > 0) {
                                return value * 100;
                            }
                            return value;
                        } catch (ParseException e2) {
                            return null;
                        }
                    }
                case FORMULA:
                    try {
                        if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                            double formulaNumValue = cell.getNumericCellValue();
                            // Si la valeur est < 1, multiplier par 100
                            if (formulaNumValue < 1 && formulaNumValue > 0) {
                                return formulaNumValue * 100;
                            }
                            return formulaNumValue;
                        } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                            String formulaStrValue = cell.getStringCellValue().trim();
                            if (formulaStrValue.isEmpty()) return null;
                            
                            // Retirer le signe % s'il existe
                            boolean formulaHasPercent = formulaStrValue.endsWith("%");
                            if (formulaHasPercent) {
                                formulaStrValue = formulaStrValue.substring(0, formulaStrValue.length() - 1).trim();
                            }
                            
                            if (!formulaStrValue.matches(".*\\d+.*")) {
                                return null;
                            }
                            
                            formulaStrValue = formulaStrValue.replace(" ", "").replace(",", ".");
                            try {
                                double value = Double.parseDouble(formulaStrValue);
                                if (!formulaHasPercent && value < 1 && value > 0) {
                                    return value * 100;
                                }
                                return value;
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                default:
                    return null;
            }
        } catch (Exception e) {
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
                    try {
                        // Vérifier si la formule retourne une valeur numérique
                        if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                            return (int) cell.getNumericCellValue();
                        } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                            // Essayer de parser depuis la string
                            String formulaStrValue = cell.getStringCellValue().trim();
                            if (formulaStrValue.isEmpty()) return null;
                            formulaStrValue = formulaStrValue.replace(" ", "");
                            
                            // Gérer le format "mois/année" (ex: "8/2025" -> extraire 8)
                            if (formulaStrValue.contains("/")) {
                                String[] parts = formulaStrValue.split("/");
                                if (parts.length >= 1) {
                                    try {
                                        return Integer.parseInt(parts[0].trim());
                                    } catch (NumberFormatException e) {
                                        // Continuer avec le parsing normal
                                    }
                                }
                            }
                            
                            try {
                                return Integer.parseInt(formulaStrValue);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }
                        return null;
                    } catch (Exception e) {
                        log.warn("Error getting integer value from formula cell: {}", e.getMessage());
                        return null;
                    }
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
        
        // Extraire la date des formats comme "LIV 6/5", "LIV MARS", etc.
        // Chercher un pattern de date dans la chaîne (DD/MM, DD/MM/YYYY, etc.)
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("(\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)");
        java.util.regex.Matcher matcher = datePattern.matcher(dateStr);
        if (matcher.find()) {
            dateStr = matcher.group(1);
        } else {
            // Si pas de pattern de date trouvé et que la chaîne contient du texte non numérique, retourner null
            if (!dateStr.matches(".*\\d+.*")) {
                return null;
            }
        }
        
        // Format DD/MM/YYYY
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER_DDMMYYYY);
        } catch (DateTimeParseException e) {
            // Continuer avec les autres formats
        }
        
        // Format DD/MM (sans année, utiliser l'année actuelle)
        try {
            if (dateStr.matches("\\d{1,2}/\\d{1,2}")) {
                String[] parts = dateStr.split("/");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = java.time.LocalDate.now().getYear();
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            // Continuer avec les autres formats
        }
        
        // Format YYYY-MM-DD
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER_YYYYMMDD);
        } catch (DateTimeParseException e) {
            // Continuer avec les autres formats
        }
        
        // Format Excel (si c'est un nombre)
        try {
            double excelDate = Double.parseDouble(dateStr);
            Date javaDate = DateUtil.getJavaDate(excelDate);
            if (javaDate != null) {
                return javaDate.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            }
        } catch (NumberFormatException e) {
            // Ce n'est pas un nombre, continuer avec les autres formats
        } catch (Exception e) {
            // Ignorer les erreurs de parsing Excel
        }
        
        // Essayer de parser les formats de date Java comme "Thu Jan 02 00:00:00 CET 2025"
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH);
            Date parsed = sdf.parse(dateStr);
            return parsed.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception e) {
            // Ignorer silencieusement si on ne peut pas parser
        }
        
        // Si aucun format ne fonctionne
        return null;
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
            
            // Créer l'en-tête selon le format exact de l'image
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "N° BC", "FRS", "N FAC FRS", "N° FAC VTE", "DATE FAC VTF", "ICE", "CLENT",
                "N° ARTI", "DESIGNATION", "U", "QT ACHAT", "PRIX ACHAT U HT",
                "PRIX ACHAT U TTC", "PRIX ACHAT T HT", "TX TVA", "FACTURE ACHAT TTC",
                "QT LIVREE CLT", "PRIX DE VENTE U HT", "FACTURE VENTE T TTC"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Créer une ligne d'exemple selon le format exact de l'image
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("PDA01AC/25"); // N° BC
            exampleRow.createCell(1).setCellValue("ACHGHAL CHAREK"); // FRS
            exampleRow.createCell(2).setCellValue("F250103"); // N FAC FRS
            exampleRow.createCell(3).setCellValue("0101/2025"); // N° FAC VTE
            exampleRow.createCell(4).setCellValue("31/01/2025"); // DATE FAC VTF
            exampleRow.createCell(5).setCellValue("001549104000010"); // ICE
            exampleRow.createCell(6).setCellValue("SONOFRERES"); // CLENT
            exampleRow.createCell(7).setCellValue("1"); // N° ARTI
            exampleRow.createCell(8).setCellValue("PIERES DE CONSTRUCTION RENDU"); // DESIGNATION
            exampleRow.createCell(9).setCellValue("T"); // U
            exampleRow.createCell(10).setCellValue(41.12); // QT ACHAT
            exampleRow.createCell(11).setCellValue(95.00); // PRIX ACHAT U HT
            exampleRow.createCell(12).setCellValue(114.00); // PRIX ACHAT U TTC
            exampleRow.createCell(13).setCellValue(3906.40); // PRIX ACHAT T HT
            exampleRow.createCell(14).setCellValue(20.0); // TX TVA
            exampleRow.createCell(15).setCellValue(4687.68); // FACTURE ACHAT TTC
            exampleRow.createCell(16).setCellValue(41.12); // QT LIVREE CLT
            exampleRow.createCell(17).setCellValue(101.65); // PRIX DE VENTE U HT
            exampleRow.createCell(18).setCellValue(5015.82); // FACTURE VENTE T TTC
            
            // Note: Le template correspond exactement au format de l'image :
            // N° BC, FRS, N FAC FRS, N° FAC VTE, DATE FAC VTF, ICE, CLENT, N° ARTI, etc.
            // DATE BC n'est plus dans le template (optionnel dans la nouvelle structure)
            
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
             Workbook workbook = createWorkbook(is)) {
            
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
                
                Map<String, Object> rowData = extractRowData(row, columnMap, headerRow);
                String errorMessage = null;
                
                try {
                    String refArticle = getCellValue(row, columnMap, "ref_article");
                    if (refArticle == null || refArticle.trim().isEmpty()) {
                        errorCount++;
                        errorMessage = "Référence article manquante";
                        result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMessage));
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage(errorMessage)
                                .build());
                        continue;
                    }
                    
                    // Nettoyer la référence pour supprimer les préfixes numériques (numéros de ligne BC)
                    // Ex: "2 - CABLE 1.5 INGELEC NOIR" -> "CABLE 1.5 INGELEC NOIR"
                    refArticle = cleanProductRef(refArticle);
                    if (refArticle.isEmpty()) {
                        errorCount++;
                        errorMessage = "Référence article invalide après nettoyage";
                        result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMessage));
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage(errorMessage)
                                .build());
                        continue;
                    }
                    
                    String designation = getCellValue(row, columnMap, "designation");
                    if (designation == null || designation.trim().isEmpty()) {
                        errorCount++;
                        errorMessage = "Désignation manquante";
                        result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMessage));
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage(errorMessage)
                                .build());
                        continue;
                    }
                    
                    String unite = getCellValue(row, columnMap, "unite");
                    if (unite == null || unite.trim().isEmpty()) {
                        unite = "U"; // Valeur par défaut
                    }
                    
                    Double prixAchat = getDoubleValue(row, columnMap, "prix_achat");
                    if (prixAchat == null || prixAchat < 0) {
                        errorCount++;
                        errorMessage = "Prix achat invalide";
                        result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMessage));
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage(errorMessage)
                                .build());
                        continue;
                    }
                    
                    Double prixVente = getDoubleValue(row, columnMap, "prix_vente");
                    if (prixVente == null || prixVente < 0) {
                        errorCount++;
                        errorMessage = "Prix vente invalide";
                        result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMessage));
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage(errorMessage)
                                .build());
                        continue;
                    }
                    
                    Double tva = getPercentageValue(row, columnMap, "tva");
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
                    
                    // Utiliser la designation comme référence si elle est disponible et différente
                    // Sinon utiliser refArticle nettoyée
                    String finalRefArticle = (designation != null && !designation.trim().isEmpty()) 
                        ? designation.trim() 
                        : refArticle.trim();
                    
                    // Vérifier si le produit existe déjà
                    Product existingProduct = productRepository.findByRefArticle(finalRefArticle)
                        .orElse(null);
                    
                    Product product;
                    if (existingProduct != null) {
                        // Mettre à jour le produit existant
                        existingProduct.setRefArticle(finalRefArticle);
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
                            .refArticle(finalRefArticle)
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
                    // Stocker la ligne de succès
                    result.getSuccessRows().add(ImportResult.SuccessRow.builder()
                            .rowNumber(i + 1)
                            .rowData(rowData)
                            .build());
                } catch (Exception e) {
                    errorCount++;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "Erreur inconnue";
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMsg));
                    log.error("Error processing product row {}: {}", i + 1, errorMsg, e);
                    
                    // Stocker la ligne en erreur
                    result.getErrorRows().add(ImportResult.ErrorRow.builder()
                            .rowNumber(i + 1)
                            .rowData(rowData)
                            .errorMessage(errorMsg)
                            .build());
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
            CellType cellType = cell.getCellType();
            
            // Vérifier d'abord le type de cellule avant d'appeler isCellDateFormatted
            // car isCellDateFormatted peut retourner true même pour STRING, ce qui cause des erreurs
            if (cellType == CellType.NUMERIC) {
                // Vérifier si c'est formaté comme date
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        Date date = cell.getDateCellValue();
                        if (date != null) {
                            return date.toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate();
                        }
                    } catch (Exception e) {
                        // Si getDateCellValue échoue, essayer comme nombre Excel
                    }
                }
                // Date stockée comme nombre Excel
                try {
                    double excelDate = cell.getNumericCellValue();
                    Date javaDate = DateUtil.getJavaDate(excelDate);
                    if (javaDate != null) {
                        return javaDate.toInstant()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate();
                    }
                } catch (Exception e) {
                    // Ignorer les erreurs
                }
            } else if (cellType == CellType.STRING) {
                // Parser depuis string
                String dateStr = cell.getStringCellValue();
                if (dateStr == null || dateStr.trim().isEmpty()) {
                    return null;
                }
                dateStr = dateStr.trim();
                // Ignorer les valeurs qui sont clairement pas des dates (une seule lettre, mots comme "AVOIR", etc.)
                if (dateStr.length() == 1 && !Character.isDigit(dateStr.charAt(0))) {
                    return null;
                }
                // Ignorer les mots qui ne contiennent pas de chiffres
                if (!dateStr.matches(".*\\d+.*")) {
                    return null;
                }
                return parseDate(dateStr);
            } else if (cellType == CellType.FORMULA) {
                // Vérifier le type de résultat de la formule
                try {
                    CellType resultType = cell.getCachedFormulaResultType();
                    if (resultType == CellType.NUMERIC) {
                        // Vérifier si c'est formaté comme date
                        if (DateUtil.isCellDateFormatted(cell)) {
                            try {
                                Date date = cell.getDateCellValue();
                                if (date != null) {
                                    return date.toInstant()
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDate();
                                }
                            } catch (Exception e) {
                                // Si getDateCellValue échoue, essayer comme nombre Excel
                            }
                        }
                        // Essayer comme nombre Excel
                        try {
                            double excelDate = cell.getNumericCellValue();
                            Date javaDate = DateUtil.getJavaDate(excelDate);
                            if (javaDate != null) {
                                return javaDate.toInstant()
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate();
                            }
                        } catch (Exception e) {
                            // Ignorer les erreurs
                        }
                    } else if (resultType == CellType.STRING) {
                        String dateStr = cell.getStringCellValue();
                        if (dateStr != null && !dateStr.trim().isEmpty() && dateStr.matches(".*\\d+.*")) {
                            return parseDate(dateStr.trim());
                        }
                    }
                } catch (Exception e) {
                    // Ignorer silencieusement les erreurs
                }
                return null;
            } else if (cellType == CellType.BLANK) {
                // Cellule vide
                return null;
            }
        } catch (Exception e) {
            // Ignorer silencieusement les erreurs de parsing de date
            // Ne pas logger car cela génère trop de warnings pour des cellules normales
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
             Workbook workbook = createWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.getErrors().add("Le fichier Excel est vide ou ne contient que l'en-tête");
                return result;
            }
            
            // Mapping intelligent des colonnes
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnMap = mapOperationsComptablesColumns(headerRow);
            log.info("Mapped operations comptables columns: {}", columnMap);
            
            // Vérifier que la colonne DATE est bien mappée
            Integer dateColIndex = columnMap.get("date");
            if (dateColIndex != null) {
                log.info("Colonne DATE trouvée à l'index: {} (colonne Excel: {})", dateColIndex, dateColIndex + 1);
            } else {
                log.warn("Colonne DATE non trouvée dans le mapping !");
            }
            
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
                        // Sauvegarder l'opération dans l'ordre chronologique (ordre du fichier Excel)
                        // Les opérations sont déjà dans l'ordre car on les traite ligne par ligne
                        operationComptableRepository.save(operation);
                        successCount++;
                        
                        // Log pour tracer la date extraite de la colonne DATE
                        if (operation.getDateOperation() != null) {
                            log.debug("Opération comptable sauvegardée - Ligne: {}, Date (colonne DATE): {}, Type: {}, Montant: {}", 
                                    i + 1, operation.getDateOperation(), operation.getTypeMouvement(), 
                                    operation.getTotalPayementTtc() != null ? operation.getTotalPayementTtc() : operation.getTotalTtcApresRg());
                        }
                        
                        // Générer le détail de l'action
                        StringBuilder actionDetail = new StringBuilder();
                        if (operation.getTypeMouvement() != null) {
                            actionDetail.append(operation.getTypeMouvement().name());
                        } else {
                            actionDetail.append("OPÉRATION");
                        }
                        actionDetail.append(" créé(e)");
                        
                        if (operation.getNumeroBc() != null && !operation.getNumeroBc().trim().isEmpty()) {
                            actionDetail.append(" - BC: ").append(operation.getNumeroBc());
                        }
                        
                        if (operation.getTotalPayementTtc() != null && operation.getTotalPayementTtc() > 0) {
                            actionDetail.append(String.format(", Montant paiement: %.2f MAD", operation.getTotalPayementTtc()));
                        } else if (operation.getTotalTtcApresRg() != null && operation.getTotalTtcApresRg() > 0) {
                            actionDetail.append(String.format(", Montant facture: %.2f MAD", operation.getTotalTtcApresRg()));
                        }
                        
                        if (operation.getReference() != null && !operation.getReference().trim().isEmpty()) {
                            actionDetail.append(", Référence: ").append(operation.getReference());
                        }
                        
                        if (operation.getDateOperation() != null) {
                            actionDetail.append(", Date: ").append(operation.getDateOperation().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                        }
                        
                        // Stocker la ligne de succès avec toutes les colonnes et le détail de l'action
                        Map<String, Object> rowData = extractAllRowData(row, headerRow);
                        result.getSuccessRows().add(ImportResult.SuccessRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .actionDetail(actionDetail.toString())
                                .build());
                    } else {
                        errorCount++;
                        // Stocker la ligne en erreur avec toutes les colonnes
                        Map<String, Object> rowData = extractAllRowData(row, headerRow);
                        result.getErrorRows().add(ImportResult.ErrorRow.builder()
                                .rowNumber(i + 1)
                                .rowData(rowData)
                                .errorMessage("Opération invalide ou incomplète")
                                .build());
                    }
                } catch (Exception e) {
                    errorCount++;
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "Erreur inconnue lors du traitement de la ligne";
                    }
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, errorMsg));
                    log.error("Error processing operation comptable row {}: {}", i + 1, errorMsg, e);
                    
                    // Stocker la ligne en erreur avec toutes les colonnes
                    Map<String, Object> rowData = extractAllRowData(row, headerRow);
                    result.getErrorRows().add(ImportResult.ErrorRow.builder()
                            .rowNumber(i + 1)
                            .rowData(rowData)
                            .errorMessage(errorMsg)
                            .build());
                }
            }
            
            result.setSuccessCount(successCount);
            result.setErrorCount(errorCount);
            
            // Phase post-import : traiter les paiements pour mettre à jour les statuts des factures
            if (successCount > 0) {
                try {
                    // Traiter les opérations CAPITAL (avant les paiements)
                    int capitalOperationsProcessed = processCapitalOperations(result);
                    log.info("Processed {} capital operations", capitalOperationsProcessed);
                    
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
     * Traite les opérations CAPITAL pour mettre à jour le capital actuel
     */
    private int processCapitalOperations(ImportResult result) {
        int capitalOperationsProcessed = 0;
        
        // Récupérer toutes les opérations avec AFFECTATION = "CAPITAL"
        List<OperationComptable> allOperations = operationComptableRepository.findAll();
        
        List<OperationComptable> capitalOperations = allOperations.stream()
                .filter(op -> {
                    String numeroBc = op.getNumeroBc();
                    return numeroBc != null && numeroBc.trim().equalsIgnoreCase("CAPITAL");
                })
                .sorted((op1, op2) -> {
                    // Trier par date (ordre chronologique croissant)
                    LocalDate date1 = op1.getDateOperation();
                    LocalDate date2 = op2.getDateOperation();
                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1;
                    if (date2 == null) return -1;
                    return date1.compareTo(date2);
                })
                .collect(java.util.stream.Collectors.toList());
        
        log.info("Found {} capital operations to process", capitalOperations.size());
        
        for (OperationComptable operation : capitalOperations) {
            try {
                Double montant = null;
                String typeOperation = operation.getTypeOperation() != null ? operation.getTypeOperation().name() : "";
                
                // Déterminer le montant selon le type de mouvement
                if (operation.getTypeMouvement() == TypeMouvement.PAIEMENT) {
                    // Pour les paiements, c'est un apport au capital (positif)
                    montant = operation.getTotalPayementTtc();
                } else if (operation.getTypeMouvement() == TypeMouvement.FACTURE) {
                    // Pour les factures, c'est un retrait de capital (négatif)
                    montant = operation.getTotalTtcApresRg() != null ? -operation.getTotalTtcApresRg() : null;
                } else {
                    // Si type mouvement est null, essayer de déterminer par les montants
                    if (operation.getTotalPayementTtc() != null && operation.getTotalPayementTtc() > 0) {
                        montant = operation.getTotalPayementTtc();
                    } else if (operation.getTotalTtcApresRg() != null && operation.getTotalTtcApresRg() > 0) {
                        montant = -operation.getTotalTtcApresRg();
                    }
                }
                
                if (montant != null && montant != 0) {
                    companyInfoService.updateCapitalActuel(montant);
                    capitalOperationsProcessed++;
                    log.info("Updated capital actuel: {} MAD (from operation type: {}, date: {})", 
                            montant, typeOperation, operation.getDateOperation());
                } else {
                    log.warn("Capital operation with no valid amount: {}", operation.getId());
                }
            } catch (Exception e) {
                log.error("Error processing capital operation {}: {}", operation.getId(), e.getMessage(), e);
                result.getWarnings().add(String.format("Erreur traitement opération CAPITAL: %s", e.getMessage()));
            }
        }
        
        return capitalOperationsProcessed;
    }
    
    /**
     * Traite les paiements depuis les opérations comptables importées
     * et met à jour les statuts des factures
     */
    
    /**
     * Vérifie si une opération est une charge (bureaux, etc.)
     */
    private boolean isChargeOperation(String numeroBc) {
        if (numeroBc == null || numeroBc.trim().isEmpty()) {
            return false;
        }
        String numeroBcLower = numeroBc.toLowerCase().trim();
        return numeroBcLower.contains("bureaux") || 
               numeroBcLower.contains("bureau") ||
               numeroBcLower.contains("charge") ||
               numeroBcLower.contains("frais") ||
               numeroBcLower.contains("loyer");
    }
    
    private int processPaymentsFromOperations(ImportResult result) {
        final java.util.concurrent.atomic.AtomicInteger paymentsProcessed = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger chargesProcessed = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // Récupérer toutes les opérations de type PAIEMENT avec un montant de paiement > 0
        List<OperationComptable> allOperations = operationComptableRepository.findAll();
        log.info("Total operations in database: {}", allOperations.size());
        
        // Filtrer les paiements et trier par date (ordre chronologique)
        List<OperationComptable> paiements = allOperations.stream()
                .filter(op -> {
                    // Vérifier le type mouvement
                    boolean isPaiement = op.getTypeMouvement() == TypeMouvement.PAIEMENT;
                    if (!isPaiement && op.getTypeMouvement() == null) {
                        // Si type mouvement est null, vérifier si c'est un paiement par le montant
                        isPaiement = op.getTotalPayementTtc() != null && op.getTotalPayementTtc() > 0
                                && (op.getTotalTtcApresRg() == null || op.getTotalTtcApresRg() == 0);
                    }
                    return isPaiement;
                })
                .filter(op -> op.getTotalPayementTtc() != null && op.getTotalPayementTtc() > 0)
                .sorted((op1, op2) -> {
                    // Trier par date (ordre chronologique croissant)
                    LocalDate date1 = op1.getDateOperation();
                    LocalDate date2 = op2.getDateOperation();
                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1; // Les dates null à la fin
                    if (date2 == null) return -1;
                    return date1.compareTo(date2);
                })
                .collect(java.util.stream.Collectors.toList());
        
        log.info("Found {} payment operations to process (sorted chronologically)", paiements.size());
        
        for (OperationComptable operation : paiements) {
            try {
                String numeroBc = operation.getNumeroBc() != null ? operation.getNumeroBc().trim() : null;
                TypeOperation typeOperation = operation.getTypeOperation();
                Double montantPaiement = operation.getTotalPayementTtc();
                LocalDate dateOperation = operation.getDateOperation();
                String commentaire = operation.getCommentaire();
                
                // Log pour tracer la date utilisée (colonne DATE de l'Excel)
                log.debug("Traitement paiement - BC: {}, Date opération (colonne DATE): {}, Montant: {}, Référence opération: {}", 
                        numeroBc, dateOperation, montantPaiement, operation.getReference());
                
                if (montantPaiement == null || montantPaiement <= 0) {
                    continue;
                }
                
                // 0. Ignorer les opérations CAPITAL (elles sont traitées dans processCapitalOperations)
                if (numeroBc != null && numeroBc.trim().equalsIgnoreCase("CAPITAL")) {
                    log.debug("Skipping CAPITAL operation in payment processing (already handled in processCapitalOperations)");
                    continue;
                }
                
                // 1. Vérifier si c'est une charge (bureaux, etc.)
                if (numeroBc != null && isChargeOperation(numeroBc)) {
                    try {
                        // Créer une charge
                        String libelle = (commentaire != null && !commentaire.trim().isEmpty()) 
                                ? commentaire.trim() 
                                : ("Charge " + numeroBc);
                        
                        // Déterminer si imposable basé sur le taux de TVA
                        // Si taux_tva est null ou 0%, alors non imposable (imposable = false)
                        // Si taux_tva > 0% (10%, 20%, etc.), alors imposable (imposable = true)
                        Double tauxTva = operation.getTauxTva();
                        Boolean imposable = (tauxTva != null && tauxTva > 0);
                        
                        Charge charge = Charge.builder()
                                .libelle(libelle)
                                .categorie("AUTRE")
                                .montant(montantPaiement)
                                .dateEcheance(dateOperation != null ? dateOperation : LocalDate.now())
                                .statut("PREVUE")
                                .imposable(imposable)
                                .tauxImposition(imposable && tauxTva != null ? tauxTva : null)
                                .notes(commentaire)
                                .build();
                        
                        Charge savedCharge = chargeService.create(charge);
                        
                        // Marquer comme payée si date de paiement est présente
                        if (dateOperation != null) {
                            chargeService.marquerPayee(savedCharge.getId(), dateOperation);
                        }
                        
                        chargesProcessed.incrementAndGet();
                        log.info("Created charge: {} - {} MAD (from BC: {})", libelle, montantPaiement, numeroBc);
                    } catch (Exception e) {
                        log.error("Error creating charge for BC {}: {}", numeroBc, e.getMessage(), e);
                        result.getWarnings().add(String.format("Erreur création charge pour BC %s: %s", numeroBc, e.getMessage()));
                    }
                    continue; // Passer à l'opération suivante
                }
                
                // 2. Si pas de BC, on ne peut pas matcher - passer à la suivante
                if (numeroBc == null || numeroBc.trim().isEmpty()) {
                    log.debug("Skipping payment operation: no BC reference (numeroBc)");
                    continue;
                }
                
                // 3. Traiter les paiements pour factures (C = Client, F = Fournisseur)
                if (typeOperation == TypeOperation.F) {
                    // Facture Achat (Fournisseur) - Chercher par bcReference
                    List<FactureAchat> factures = factureAchatRepository.findByBcReference(numeroBc);
                    
                    if (factures.isEmpty()) {
                        log.warn("No facture achat found for BC: {}", numeroBc);
                        result.getWarnings().add(String.format("Aucune facture achat trouvée pour BC: %s", numeroBc));
                        
                        // Capturer la facture non trouvée pour le rapport
                        Map<String, Object> operationData = new HashMap<>();
                        operationData.put("numeroBc", numeroBc);
                        operationData.put("numeroFacture", operation.getNumeroFacture());
                        operationData.put("reference", operation.getReference());
                        operationData.put("partenaire", operation.getNomClientFrs());
                        operationData.put("typeOperation", "F");
                        operationData.put("montant", montantPaiement);
                        operationData.put("dateOperation", dateOperation);
                        operationData.put("moyenPayement", operation.getMoyenPayement());
                        operationData.put("commentaire", commentaire);
                        
                        result.getNotFoundInvoices().add(ImportResult.NotFoundInvoice.builder()
                                .numeroBc(numeroBc)
                                .numeroFacture(operation.getNumeroFacture())
                                .reference(operation.getReference())
                                .partenaire(operation.getNomClientFrs())
                                .typeOperation("F")
                                .montant(montantPaiement)
                                .dateOperation(dateOperation)
                                .raison("Aucune facture achat trouvée pour BC: " + numeroBc)
                                .operationData(operationData)
                                .build());
                        continue;
                    }
                    
                    // Si plusieurs factures, utiliser la première ou matcher par montant
                    FactureAchat facture = factures.size() == 1 
                            ? factures.get(0)
                            : factures.stream()
                                    .filter(f -> f.getTotalTTC() != null && 
                                            Math.abs(f.getTotalTTC() - montantPaiement) < 0.01)
                                    .findFirst()
                                    .orElse(factures.get(0));
                    
                    if (factures.size() > 1) {
                        log.warn("Multiple factures achat found for BC {}: using first match or by amount", numeroBc);
                    }
                    
                    try {
                        // Vérifier si un paiement existe déjà
                        boolean paiementExiste = false;
                        List<Paiement> paiementsExistants = paiementService.findByFactureAchatId(facture.getId());
                        if (operation.getReference() != null && !operation.getReference().trim().isEmpty()) {
                            paiementExiste = paiementsExistants.stream()
                                    .anyMatch(p -> operation.getReference().equals(p.getReference()));
                        } else {
                            paiementExiste = paiementsExistants.stream()
                                    .anyMatch(p -> p.getDate() != null && p.getDate().equals(dateOperation)
                                            && p.getMontant() != null && Math.abs(p.getMontant() - montantPaiement) < 0.01);
                        }
                        
                        if (!paiementExiste) {
                            Paiement paiement = Paiement.builder()
                                    .factureAchatId(facture.getId())
                                    .bcReference(numeroBc)
                                    .typeMouvement("F")
                                    .nature("paiement")
                                    .date(dateOperation)
                                    .montant(montantPaiement)
                                    .mode(operation.getMoyenPayement())
                                    .reference(operation.getReference())
                                    .tvaRate(operation.getTauxTva())
                                    .totalPaiementTTC(montantPaiement)
                                    .htPaye(operation.getHtPaye())
                                    .tvaPaye(operation.getTva())
                                    .notes(commentaire)
                                    .build();
                            
                            paiementService.create(paiement);
                            paymentsProcessed.incrementAndGet();
                            log.info("Created payment for facture achat {} (BC: {}): {} MAD - Date: {} (colonne DATE)", 
                                    facture.getNumeroFactureAchat(), numeroBc, montantPaiement, dateOperation);
                        } else {
                            log.debug("Payment already exists for facture achat {} (BC: {})", 
                                    facture.getNumeroFactureAchat(), numeroBc);
                        }
                    } catch (Exception e) {
                        log.error("Error creating payment for facture achat (BC: {}): {}", numeroBc, e.getMessage(), e);
                        result.getWarnings().add(String.format("Erreur création paiement facture achat (BC %s): %s", numeroBc, e.getMessage()));
                    }
                    
                } else if (typeOperation == TypeOperation.C) {
                    // Facture Vente (Client) - Chercher par bcReference
                    List<FactureVente> factures = factureVenteRepository.findByBcReference(numeroBc);
                    
                    if (factures.isEmpty()) {
                        log.warn("No facture vente found for BC: {}", numeroBc);
                        result.getWarnings().add(String.format("Aucune facture vente trouvée pour BC: %s", numeroBc));
                        
                        // Capturer la facture non trouvée pour le rapport
                        Map<String, Object> operationData = new HashMap<>();
                        operationData.put("numeroBc", numeroBc);
                        operationData.put("numeroFacture", operation.getNumeroFacture());
                        operationData.put("reference", operation.getReference());
                        operationData.put("partenaire", operation.getNomClientFrs());
                        operationData.put("typeOperation", "C");
                        operationData.put("montant", montantPaiement);
                        operationData.put("dateOperation", dateOperation);
                        operationData.put("moyenPayement", operation.getMoyenPayement());
                        operationData.put("commentaire", commentaire);
                        
                        result.getNotFoundInvoices().add(ImportResult.NotFoundInvoice.builder()
                                .numeroBc(numeroBc)
                                .numeroFacture(operation.getNumeroFacture())
                                .reference(operation.getReference())
                                .partenaire(operation.getNomClientFrs())
                                .typeOperation("C")
                                .montant(montantPaiement)
                                .dateOperation(dateOperation)
                                .raison("Aucune facture vente trouvée pour BC: " + numeroBc)
                                .operationData(operationData)
                                .build());
                        continue;
                    }
                    
                    // Si plusieurs factures, utiliser la première ou matcher par montant
                    FactureVente facture = factures.size() == 1 
                            ? factures.get(0)
                            : factures.stream()
                                    .filter(f -> f.getTotalTTC() != null && 
                                            Math.abs(f.getTotalTTC() - montantPaiement) < 0.01)
                                    .findFirst()
                                    .orElse(factures.get(0));
                    
                    if (factures.size() > 1) {
                        log.warn("Multiple factures vente found for BC {}: using first match or by amount", numeroBc);
                    }
                    
                    try {
                        // Vérifier si un paiement existe déjà
                        boolean paiementExiste = false;
                        List<Paiement> paiementsExistants = paiementService.findByFactureVenteId(facture.getId());
                        if (operation.getReference() != null && !operation.getReference().trim().isEmpty()) {
                            paiementExiste = paiementsExistants.stream()
                                    .anyMatch(p -> operation.getReference().equals(p.getReference()));
                        } else {
                            paiementExiste = paiementsExistants.stream()
                                    .anyMatch(p -> p.getDate() != null && p.getDate().equals(dateOperation)
                                            && p.getMontant() != null && Math.abs(p.getMontant() - montantPaiement) < 0.01);
                        }
                        
                        if (!paiementExiste) {
                            Paiement paiement = Paiement.builder()
                                    .factureVenteId(facture.getId())
                                    .bcReference(numeroBc)
                                    .typeMouvement("C")
                                    .nature("paiement")
                                    .date(dateOperation)
                                    .montant(montantPaiement)
                                    .mode(operation.getMoyenPayement())
                                    .reference(operation.getReference())
                                    .tvaRate(operation.getTauxTva())
                                    .totalPaiementTTC(montantPaiement)
                                    .htPaye(operation.getHtPaye())
                                    .tvaPaye(operation.getTva())
                                    .notes(commentaire)
                                    .build();
                            
                            paiementService.create(paiement);
                            paymentsProcessed.incrementAndGet();
                            log.info("Created payment for facture vente {} (BC: {}): {} MAD - Date: {} (colonne DATE)", 
                                    facture.getNumeroFactureVente(), numeroBc, montantPaiement, dateOperation);
                        } else {
                            log.debug("Payment already exists for facture vente {} (BC: {})", 
                                    facture.getNumeroFactureVente(), numeroBc);
                        }
                    } catch (Exception e) {
                        log.error("Error creating payment for facture vente (BC: {}): {}", numeroBc, e.getMessage(), e);
                        result.getWarnings().add(String.format("Erreur création paiement facture vente (BC %s): %s", numeroBc, e.getMessage()));
                    }
                } else {
                    log.debug("Skipping payment operation: typeOperation is not C or F: {}", typeOperation);
                }
                
            } catch (Exception e) {
                log.error("Error processing payment operation: {}", e.getMessage(), e);
                result.getWarnings().add("Erreur traitement paiement: " + e.getMessage());
            }
        }
        
        log.info("Processed {} payments and {} charges from imported operations", 
                paymentsProcessed.get(), chargesProcessed.get());
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
                log.debug("Colonne DATE mappée à l'index: {} (colonne Excel: {})", colIndex, colIndex + 1);
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
            throw new RuntimeException("Type opération manquant (C, F, IS, TVA, CNSS, FB, LOY, IT, S)");
        }
        TypeOperation typeOperation;
        try {
            typeOperation = TypeOperation.valueOf(typeOpStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Type opération invalide: " + typeOpStr + ". Valeurs possibles: C, F, IS, TVA, CNSS, FB, LOY, IT, S");
        }
        
        // Source paiement
        String sourcePayement = getCellValue(row, columnMap, "source_payement");
        
        // Date (obligatoire) - Colonne 7 (colonne "DATE")
        LocalDate dateOperation = null;
        Integer dateColIndex = columnMap.get("date");
        if (dateColIndex != null) {
            Cell dateCell = row.getCell(dateColIndex);
            if (dateCell != null) {
                dateOperation = parseDateFromCell(dateCell);
                if (dateOperation != null) {
                    log.debug("Date extraite depuis cellule DATE (colonne {}): {}", dateColIndex, dateOperation);
                }
            }
        }
        if (dateOperation == null) {
            String dateStr = getCellValue(row, columnMap, "date");
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                dateOperation = parseDate(dateStr);
                if (dateOperation != null) {
                    log.debug("Date extraite depuis string DATE: {} -> {}", dateStr, dateOperation);
                }
            }
        }
        if (dateOperation == null) {
            log.error("Date invalide ou manquante dans la colonne DATE (colonne index: {})", dateColIndex);
            throw new RuntimeException("Date invalide ou manquante dans la colonne DATE");
        }
        log.debug("Date finale utilisée pour l'opération: {}", dateOperation);
        
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
        Double tauxTva = getPercentageValue(row, columnMap, "taux_tva");
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
        Double tva = getPercentageValue(row, columnMap, "tva");
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
                        // Vérifier le type de résultat de la formule
                        if (bilanCell.getCachedFormulaResultType() == CellType.NUMERIC) {
                            bilan = bilanCell.getNumericCellValue();
                        } else if (bilanCell.getCachedFormulaResultType() == CellType.STRING) {
                            // Essayer de parser depuis la string
                            String formulaStr = bilanCell.getStringCellValue().trim();
                            if (!formulaStr.isEmpty()) {
                                // Retirer tous les caractères sauf chiffres, virgule, point, moins et espaces
                                String numStr = formulaStr.replaceAll("[^0-9,.-\\s]", "").trim();
                                numStr = numStr.replace(",", ".").replace(" ", "");
                                if (!numStr.isEmpty()) {
                                    try {
                                        bilan = Double.parseDouble(numStr);
                                    } catch (NumberFormatException e) {
                                        // Ignorer si on ne peut pas parser
                                    }
                                }
                            }
                        }
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
    
    /**
     * Extrait les données d'une ligne Excel en Map (colonne -> valeur)
     */
    private Map<String, Object> extractRowData(Row row, Map<String, Integer> columnMap, Row headerRow) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        
        if (headerRow != null) {
            // Extraire les valeurs selon les colonnes mappées
            for (Map.Entry<String, Integer> entry : columnMap.entrySet()) {
                String columnName = entry.getKey();
                int colIndex = entry.getValue();
                
                Cell cell = row.getCell(colIndex);
                Object value = null;
                
                if (cell != null) {
                    switch (cell.getCellType()) {
                        case STRING:
                            value = cell.getStringCellValue();
                            break;
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                value = cell.getDateCellValue();
                            } else {
                                value = cell.getNumericCellValue();
                            }
                            break;
                        case BOOLEAN:
                            value = cell.getBooleanCellValue();
                            break;
                        case FORMULA:
                            value = cell.getCellFormula();
                            break;
                        default:
                            value = getCellStringValue(cell);
                    }
                }
                
                rowData.put(columnName, value);
            }
            
            // Ajouter aussi toutes les autres colonnes de la ligne
            for (int i = 0; i < row.getLastCellNum(); i++) {
                Cell headerCell = headerRow.getCell(i);
                if (headerCell != null && !columnMap.containsValue(i)) {
                    String headerName = getCellStringValue(headerCell);
                    if (headerName != null && !headerName.trim().isEmpty()) {
                        Cell cell = row.getCell(i);
                        Object value = null;
                        if (cell != null) {
                            value = getCellStringValue(cell);
                        }
                        rowData.put(headerName, value);
                    }
                }
            }
        }
        
        return rowData;
    }
    
    /**
     * Extrait toutes les colonnes d'une ligne basée sur le header (toutes les colonnes, pas seulement celles mappées)
     * Utilisé pour le rapport d'import détaillé
     */
    private Map<String, Object> extractAllRowData(Row row, Row headerRow) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        
        if (headerRow == null || row == null) {
            return rowData;
        }
        
        // Itérer sur toutes les cellules du header
        int maxCol = Math.max(row.getLastCellNum(), headerRow.getLastCellNum());
        for (int i = 0; i < maxCol; i++) {
            Cell headerCell = headerRow.getCell(i);
            if (headerCell == null) {
                // Colonne sans header, utiliser un nom par défaut
                String headerName = "Colonne_" + (i + 1);
                Cell cell = row.getCell(i);
                Object value = getCellValue(cell);
                rowData.put(headerName, value);
            } else {
                String headerName = getCellStringValue(headerCell);
                if (headerName == null || headerName.trim().isEmpty()) {
                    headerName = "Colonne_" + (i + 1);
                }
                
                Cell cell = row.getCell(i);
                Object value = getCellValue(cell);
                rowData.put(headerName, value);
            }
        }
        
        return rowData;
    }
    
    /**
     * Extrait la valeur d'une cellule avec le bon type
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue();
                    } else {
                        return cell.getNumericCellValue();
                    }
                case BOOLEAN:
                    return cell.getBooleanCellValue();
                case FORMULA:
                    // Pour les formules, essayer d'obtenir la valeur calculée
                    try {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            return cell.getDateCellValue();
                        } else if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                            return cell.getNumericCellValue();
                        } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                            return cell.getStringCellValue();
                        } else if (cell.getCachedFormulaResultType() == CellType.BOOLEAN) {
                            return cell.getBooleanCellValue();
                        }
                    } catch (Exception e) {
                        // Si erreur, retourner la formule comme string
                    }
                    return cell.getCellFormula();
                default:
                    return getCellStringValue(cell);
            }
        } catch (Exception e) {
            return getCellStringValue(cell);
        }
    }
    
    /**
     * Génère un fichier Excel de rapport d'import avec les lignes en erreur et les lignes importées avec succès
     */
    public byte[] generateImportReport(ImportResult result, MultipartFile originalFile) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
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
            
            // Style pour les lignes en erreur
            CellStyle errorStyle = workbook.createCellStyle();
            Font errorFont = workbook.createFont();
            errorFont.setColor(IndexedColors.WHITE.getIndex());
            errorStyle.setFont(errorFont);
            errorStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            errorStyle.setBorderBottom(BorderStyle.THIN);
            errorStyle.setBorderTop(BorderStyle.THIN);
            errorStyle.setBorderLeft(BorderStyle.THIN);
            errorStyle.setBorderRight(BorderStyle.THIN);
            
            // Style pour les lignes de succès
            CellStyle successStyle = workbook.createCellStyle();
            Font successFont = workbook.createFont();
            successFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            successStyle.setFont(successFont);
            successStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            successStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            successStyle.setBorderBottom(BorderStyle.THIN);
            successStyle.setBorderTop(BorderStyle.THIN);
            successStyle.setBorderLeft(BorderStyle.THIN);
            successStyle.setBorderRight(BorderStyle.THIN);
            
            // Style pour les données
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            
            // Style pour les lignes ignorées
            CellStyle ignoredStyle = workbook.createCellStyle();
            Font ignoredFont = workbook.createFont();
            ignoredFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            ignoredStyle.setFont(ignoredFont);
            ignoredStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            ignoredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            ignoredStyle.setBorderBottom(BorderStyle.THIN);
            ignoredStyle.setBorderTop(BorderStyle.THIN);
            ignoredStyle.setBorderLeft(BorderStyle.THIN);
            ignoredStyle.setBorderRight(BorderStyle.THIN);
            
            // Feuille 1: Résumé
            Sheet summarySheet = workbook.createSheet("Résumé");
            int summaryRowNum = 0;
            
            Row summaryHeader = summarySheet.createRow(summaryRowNum++);
            summaryHeader.createCell(0).setCellValue("Statistiques de l'import");
            CellStyle summaryHeaderStyle = workbook.createCellStyle();
            Font summaryHeaderFont = workbook.createFont();
            summaryHeaderFont.setBold(true);
            summaryHeaderFont.setFontHeightInPoints((short) 14);
            summaryHeaderStyle.setFont(summaryHeaderFont);
            summaryHeader.getCell(0).setCellStyle(summaryHeaderStyle);
            
            summarySheet.createRow(summaryRowNum++); // Ligne vide
            
            CellStyle summaryDataStyle = workbook.createCellStyle();
            summaryDataStyle.setBorderBottom(BorderStyle.THIN);
            summaryDataStyle.setBorderTop(BorderStyle.THIN);
            summaryDataStyle.setBorderLeft(BorderStyle.THIN);
            summaryDataStyle.setBorderRight(BorderStyle.THIN);
            
            Row totalRow = summarySheet.createRow(summaryRowNum++);
            totalRow.createCell(0).setCellValue("Total des lignes:");
            totalRow.createCell(1).setCellValue(result.getTotalRows());
            totalRow.getCell(0).setCellStyle(summaryDataStyle);
            totalRow.getCell(1).setCellStyle(summaryDataStyle);
            
            Row successRow = summarySheet.createRow(summaryRowNum++);
            successRow.createCell(0).setCellValue("Lignes importées avec succès:");
            successRow.createCell(1).setCellValue(result.getSuccessCount());
            successRow.getCell(0).setCellStyle(summaryDataStyle);
            successRow.getCell(1).setCellStyle(successStyle);
            
            Row errorRow = summarySheet.createRow(summaryRowNum++);
            errorRow.createCell(0).setCellValue("Lignes en erreur:");
            errorRow.createCell(1).setCellValue(result.getErrorCount());
            errorRow.getCell(0).setCellStyle(summaryDataStyle);
            errorRow.getCell(1).setCellStyle(errorStyle);
            
            Row notFoundRow = summarySheet.createRow(summaryRowNum++);
            notFoundRow.createCell(0).setCellValue("Factures non trouvées:");
            notFoundRow.createCell(1).setCellValue(result.getNotFoundInvoices().size());
            notFoundRow.getCell(0).setCellStyle(summaryDataStyle);
            notFoundRow.getCell(1).setCellStyle(summaryDataStyle);
            
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);
            
            // Feuille 2: Toutes les lignes (succès + erreurs)
            Sheet allRowsSheet = workbook.createSheet("Toutes les lignes");
            int allRowsRowNum = 0;
            
            // Créer une Map pour fusionner toutes les lignes par numéro de ligne
            Map<Integer, ImportResult.SuccessRow> successRowsMap = new LinkedHashMap<>();
            for (ImportResult.SuccessRow sr : result.getSuccessRows()) {
                successRowsMap.put(sr.getRowNumber(), sr);
            }
            Map<Integer, ImportResult.ErrorRow> errorRowsMap = new LinkedHashMap<>();
            for (ImportResult.ErrorRow er : result.getErrorRows()) {
                errorRowsMap.put(er.getRowNumber(), er);
            }
            
            // Collecter toutes les colonnes uniques de toutes les lignes
            Set<String> allColumnNamesAllRows = new LinkedHashSet<>();
            for (ImportResult.SuccessRow sr : result.getSuccessRows()) {
                if (sr.getRowData() != null) {
                    allColumnNamesAllRows.addAll(sr.getRowData().keySet());
                }
            }
            for (ImportResult.ErrorRow er : result.getErrorRows()) {
                if (er.getRowData() != null) {
                    allColumnNamesAllRows.addAll(er.getRowData().keySet());
                }
            }
            
            // En-tête pour "Toutes les lignes"
            Row allRowsHeaderRow = allRowsSheet.createRow(allRowsRowNum++);
            int allRowsColNum = 0;
            allRowsHeaderRow.createCell(allRowsColNum++).setCellValue("N° Ligne");
            allRowsHeaderRow.createCell(allRowsColNum++).setCellValue("Statut");
            allRowsHeaderRow.createCell(allRowsColNum++).setCellValue("Action / Message");
            
            // Ajouter toutes les colonnes
            for (String colName : allColumnNamesAllRows) {
                allRowsHeaderRow.createCell(allRowsColNum++).setCellValue(colName);
            }
            
            // Appliquer le style d'en-tête
            for (int i = 0; i < allRowsColNum; i++) {
                allRowsHeaderRow.getCell(i).setCellStyle(headerStyle);
            }
            
            // Trier toutes les lignes par numéro de ligne
            Set<Integer> allRowNumbers = new TreeSet<>();
            allRowNumbers.addAll(successRowsMap.keySet());
            allRowNumbers.addAll(errorRowsMap.keySet());
            
            // Ajouter les lignes dans l'ordre
            for (Integer rowNum : allRowNumbers) {
                Row row = allRowsSheet.createRow(allRowsRowNum++);
                allRowsColNum = 0;
                CellStyle rowStyle = dataStyle;
                String status = "";
                String actionMsg = "";
                
                ImportResult.SuccessRow currentSuccessRow = successRowsMap.get(rowNum);
                ImportResult.ErrorRow currentErrorRow = errorRowsMap.get(rowNum);
                Map<String, Object> rowData = null;
                
                if (currentErrorRow != null) {
                    // Ligne en erreur
                    rowStyle = errorStyle;
                    status = "ERREUR";
                    actionMsg = currentErrorRow.getErrorMessage() != null ? currentErrorRow.getErrorMessage() : "";
                    rowData = currentErrorRow.getRowData();
                } else if (currentSuccessRow != null) {
                    // Ligne de succès
                    rowStyle = successStyle;
                    status = "SUCCÈS";
                    actionMsg = currentSuccessRow.getActionDetail() != null ? currentSuccessRow.getActionDetail() : "";
                    rowData = currentSuccessRow.getRowData();
                } else {
                    // Ne devrait pas arriver
                    continue;
                }
                
                // N° ligne
                Cell cell = row.createCell(allRowsColNum++);
                cell.setCellValue(rowNum);
                cell.setCellStyle(rowStyle);
                
                // Statut
                cell = row.createCell(allRowsColNum++);
                cell.setCellValue(status);
                cell.setCellStyle(rowStyle);
                
                // Action / Message
                cell = row.createCell(allRowsColNum++);
                cell.setCellValue(actionMsg);
                cell.setCellStyle(rowStyle);
                
                // Données de la ligne
                if (rowData != null) {
                    for (String colName : allColumnNamesAllRows) {
                        cell = row.createCell(allRowsColNum++);
                        Object value = rowData.get(colName);
                        if (value != null) {
                            if (value instanceof String) {
                                cell.setCellValue((String) value);
                            } else if (value instanceof Number) {
                                cell.setCellValue(((Number) value).doubleValue());
                            } else if (value instanceof Boolean) {
                                cell.setCellValue((Boolean) value);
                            } else if (value instanceof Date) {
                                cell.setCellValue((Date) value);
                                CellStyle dateStyle = workbook.createCellStyle();
                                dateStyle.cloneStyleFrom(rowStyle);
                                dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
                                cell.setCellStyle(dateStyle);
                            } else {
                                cell.setCellValue(value.toString());
                            }
                        }
                        cell.setCellStyle(rowStyle);
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < allRowsColNum; i++) {
                allRowsSheet.autoSizeColumn(i);
            }
            
            // Feuille 3: Lignes en erreur
            Sheet errorSheet = workbook.createSheet("Lignes en erreur");
            int rowNum = 0;
            
            // En-tête
            Row headerRow = errorSheet.createRow(rowNum++);
            int colNum = 0;
            headerRow.createCell(colNum++).setCellValue("N° Ligne");
            headerRow.createCell(colNum++).setCellValue("Message d'erreur");
            
            // Récupérer tous les noms de colonnes uniques
            Set<String> allColumnNames = new LinkedHashSet<>();
            for (ImportResult.ErrorRow errRow : result.getErrorRows()) {
                if (errRow.getRowData() != null) {
                    allColumnNames.addAll(errRow.getRowData().keySet());
                }
            }
            
            // Ajouter les colonnes de données
            for (String colName : allColumnNames) {
                headerRow.createCell(colNum++).setCellValue(colName);
            }
            
            // Appliquer le style d'en-tête
            for (int i = 0; i < colNum; i++) {
                headerRow.getCell(i).setCellStyle(headerStyle);
            }
            
            // Lignes en erreur
            for (ImportResult.ErrorRow errRow : result.getErrorRows()) {
                Row row = errorSheet.createRow(rowNum++);
                colNum = 0;
                
                // N° ligne
                Cell cell0 = row.createCell(colNum++);
                cell0.setCellValue(errRow.getRowNumber());
                cell0.setCellStyle(errorStyle);
                
                // Message d'erreur
                Cell cell1 = row.createCell(colNum++);
                cell1.setCellValue(errRow.getErrorMessage() != null ? errRow.getErrorMessage() : "");
                cell1.setCellStyle(errorStyle);
                
                // Données de la ligne
                if (errRow.getRowData() != null) {
                    for (String colName : allColumnNames) {
                        Cell cell = row.createCell(colNum++);
                        Object value = errRow.getRowData().get(colName);
                        if (value != null) {
                            if (value instanceof String) {
                                cell.setCellValue((String) value);
                            } else if (value instanceof Number) {
                                cell.setCellValue(((Number) value).doubleValue());
                            } else if (value instanceof Boolean) {
                                cell.setCellValue((Boolean) value);
                            } else if (value instanceof Date) {
                                cell.setCellValue((Date) value);
                                CellStyle dateStyle = workbook.createCellStyle();
                                dateStyle.cloneStyleFrom(dataStyle);
                                dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
                                cell.setCellStyle(dateStyle);
                            } else {
                                cell.setCellValue(value.toString());
                            }
                        }
                        cell.setCellStyle(dataStyle);
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < colNum; i++) {
                errorSheet.autoSizeColumn(i);
            }
            
            // Feuille 2: Factures non trouvées
            if (!result.getNotFoundInvoices().isEmpty()) {
                Sheet notFoundSheet = workbook.createSheet("Factures non trouvées");
                rowNum = 0;
                
                // En-tête
                Row notFoundHeaderRow = notFoundSheet.createRow(rowNum++);
                colNum = 0;
                notFoundHeaderRow.createCell(colNum++).setCellValue("N° BC");
                notFoundHeaderRow.createCell(colNum++).setCellValue("N° Facture");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Référence");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Partenaire");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Type");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Montant");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Date");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Moyen de paiement");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Commentaire");
                notFoundHeaderRow.createCell(colNum++).setCellValue("Raison");
                
                // Appliquer le style d'en-tête
                for (int i = 0; i < colNum; i++) {
                    notFoundHeaderRow.getCell(i).setCellStyle(headerStyle);
                }
                
                // Style pour les lignes de factures non trouvées
                CellStyle notFoundStyle = workbook.createCellStyle();
                Font notFoundFont = workbook.createFont();
                notFoundFont.setColor(IndexedColors.DARK_RED.getIndex());
                notFoundStyle.setFont(notFoundFont);
                notFoundStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                notFoundStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                notFoundStyle.setBorderBottom(BorderStyle.THIN);
                notFoundStyle.setBorderTop(BorderStyle.THIN);
                notFoundStyle.setBorderLeft(BorderStyle.THIN);
                notFoundStyle.setBorderRight(BorderStyle.THIN);
                
                // Lignes de factures non trouvées
                for (ImportResult.NotFoundInvoice notFound : result.getNotFoundInvoices()) {
                    Row row = notFoundSheet.createRow(rowNum++);
                    colNum = 0;
                    
                    // N° BC
                    Cell cell = row.createCell(colNum++);
                    cell.setCellValue(notFound.getNumeroBc() != null ? notFound.getNumeroBc() : "");
                    cell.setCellStyle(notFoundStyle);
                    
                    // N° Facture
                    cell = row.createCell(colNum++);
                    cell.setCellValue(notFound.getNumeroFacture() != null ? notFound.getNumeroFacture() : "");
                    cell.setCellStyle(notFoundStyle);
                    
                    // Référence
                    cell = row.createCell(colNum++);
                    cell.setCellValue(notFound.getReference() != null ? notFound.getReference() : "");
                    cell.setCellStyle(notFoundStyle);
                    
                    // Partenaire
                    cell = row.createCell(colNum++);
                    cell.setCellValue(notFound.getPartenaire() != null ? notFound.getPartenaire() : "");
                    cell.setCellStyle(notFoundStyle);
                    
                    // Type
                    cell = row.createCell(colNum++);
                    cell.setCellValue(notFound.getTypeOperation() != null ? 
                            (notFound.getTypeOperation().equals("C") ? "Client" : "Fournisseur") : "");
                    cell.setCellStyle(notFoundStyle);
                    
                    // Montant
                    cell = row.createCell(colNum++);
                    if (notFound.getMontant() != null) {
                        cell.setCellValue(notFound.getMontant());
                    }
                    cell.setCellStyle(notFoundStyle);
                    
                    // Date
                    cell = row.createCell(colNum++);
                    if (notFound.getDateOperation() != null) {
                        cell.setCellValue(java.sql.Date.valueOf(notFound.getDateOperation()));
                        CellStyle dateStyle = workbook.createCellStyle();
                        dateStyle.cloneStyleFrom(notFoundStyle);
                        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
                        cell.setCellStyle(dateStyle);
                    }
                    cell.setCellStyle(notFoundStyle);
                    
                    // Moyen de paiement
                    cell = row.createCell(colNum++);
                    if (notFound.getOperationData() != null && notFound.getOperationData().containsKey("moyenPayement")) {
                        Object moyenPayement = notFound.getOperationData().get("moyenPayement");
                        cell.setCellValue(moyenPayement != null ? moyenPayement.toString() : "");
                    }
                    cell.setCellStyle(notFoundStyle);
                    
                    // Commentaire
                    cell = row.createCell(colNum++);
                    if (notFound.getOperationData() != null && notFound.getOperationData().containsKey("commentaire")) {
                        Object commentaire = notFound.getOperationData().get("commentaire");
                        cell.setCellValue(commentaire != null ? commentaire.toString() : "");
                    }
                    cell.setCellStyle(notFoundStyle);
                    
                    // Raison
                    cell = row.createCell(colNum++);
                    cell.setCellValue(notFound.getRaison() != null ? notFound.getRaison() : "");
                    cell.setCellStyle(notFoundStyle);
                }
                
                // Auto-size columns
                for (int i = 0; i < colNum; i++) {
                    notFoundSheet.autoSizeColumn(i);
                }
            }
            
            // Feuille 4: Lignes importées avec succès (optionnel)
            if (!result.getSuccessRows().isEmpty()) {
                Sheet successSheet = workbook.createSheet("Lignes importées");
                rowNum = 0;
                
                // En-tête
                headerRow = successSheet.createRow(rowNum++);
                colNum = 0;
                headerRow.createCell(colNum++).setCellValue("N° Ligne");
                headerRow.createCell(colNum++).setCellValue("Action Effectuée");
                
                // Récupérer tous les noms de colonnes uniques
                Set<String> successColumnNames = new LinkedHashSet<>();
                for (ImportResult.SuccessRow succRow : result.getSuccessRows()) {
                    if (succRow.getRowData() != null) {
                        successColumnNames.addAll(succRow.getRowData().keySet());
                    }
                }
                
                // Ajouter les colonnes de données
                for (String colName : successColumnNames) {
                    headerRow.createCell(colNum++).setCellValue(colName);
                }
                
                // Appliquer le style d'en-tête
                for (int i = 0; i < colNum; i++) {
                    headerRow.getCell(i).setCellStyle(headerStyle);
                }
                
                // Lignes de succès
                for (ImportResult.SuccessRow succRow : result.getSuccessRows()) {
                    Row row = successSheet.createRow(rowNum++);
                    colNum = 0;
                    
                    // N° ligne
                    Cell cell0 = row.createCell(colNum++);
                    cell0.setCellValue(succRow.getRowNumber());
                    cell0.setCellStyle(successStyle);
                    
                    // Action Effectuée
                    Cell cell1 = row.createCell(colNum++);
                    cell1.setCellValue(succRow.getActionDetail() != null ? succRow.getActionDetail() : "");
                    cell1.setCellStyle(successStyle);
                    
                    // Données de la ligne
                    if (succRow.getRowData() != null) {
                        for (String colName : successColumnNames) {
                            Cell cell = row.createCell(colNum++);
                            Object value = succRow.getRowData().get(colName);
                            if (value != null) {
                                if (value instanceof String) {
                                    cell.setCellValue((String) value);
                                } else if (value instanceof Number) {
                                    cell.setCellValue(((Number) value).doubleValue());
                                } else if (value instanceof Boolean) {
                                    cell.setCellValue((Boolean) value);
                                } else if (value instanceof Date) {
                                    cell.setCellValue((Date) value);
                                    CellStyle dateStyle = workbook.createCellStyle();
                                    dateStyle.cloneStyleFrom(dataStyle);
                                    dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
                                    cell.setCellStyle(dateStyle);
                                } else {
                                    cell.setCellValue(value.toString());
                                }
                            }
                            cell.setCellStyle(dataStyle);
                        }
                    }
                }
                
                // Auto-size columns
                for (int i = 0; i < colNum; i++) {
                    successSheet.autoSizeColumn(i);
                }
            }
            
            // Écrire dans un ByteArrayOutputStream
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating import report", e);
            throw new RuntimeException("Erreur lors de la génération du rapport d'import: " + e.getMessage(), e);
        }
    }
    
    /**
     * Classe interne pour agréger les données d'un produit lors de l'import.
     * Permet de dédupliquer les produits et de calculer les prix pondérés.
     */
    private static class ProductAggregate {
        String produitRef;
        String designation;
        String unite;
        Double quantiteAcheteeTotale = 0.0;
        Double quantiteVendueTotale = 0.0;
        Double sommePrixAchatPondere = 0.0; // somme(prix * qty) pour calcul pondéré
        Double sommePrixVentePondere = 0.0; // somme(prix * qty) pour calcul pondéré
        Double tva;
        
        // Structure pour gérer les ventes par client (plusieurs factures vente possibles)
        // Map<clientId, List<VenteInfo>> où VenteInfo contient numeroFV, quantite, prix
        Map<String, List<VenteInfo>> ventesParClient = new HashMap<>();
        
        /**
         * Ajoute une quantité achetée avec son prix pour le calcul pondéré
         */
        void addAchat(Double quantite, Double prixUnitaireHT) {
            if (quantite != null && quantite > 0 && prixUnitaireHT != null && prixUnitaireHT > 0) {
                quantiteAcheteeTotale += quantite;
                sommePrixAchatPondere += prixUnitaireHT * quantite;
            }
        }
        
        /**
         * Ajoute une vente pour un client donné (peut être appelé plusieurs fois pour plusieurs factures)
         */
        void addVente(String clientId, String numeroFV, Double quantite, Double prixUnitaireHT) {
            if (quantite != null && quantite > 0 && prixUnitaireHT != null && prixUnitaireHT > 0) {
                quantiteVendueTotale += quantite;
                sommePrixVentePondere += prixUnitaireHT * quantite;
                
                // Ajouter à la liste des ventes pour ce client
                ventesParClient.computeIfAbsent(clientId, k -> new ArrayList<>())
                    .add(new VenteInfo(numeroFV, quantite, prixUnitaireHT));
            }
        }
        
        /**
         * Calcule le prix d'achat pondéré (arrondi à 2 décimales)
         */
        Double getPrixAchatPondere() {
            if (quantiteAcheteeTotale <= 0) return null;
            Double prix = sommePrixAchatPondere / quantiteAcheteeTotale;
            return BigDecimal.valueOf(prix)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        }
        
        /**
         * Calcule le prix de vente pondéré (arrondi à 2 décimales)
         */
        Double getPrixVentePondere() {
            if (quantiteVendueTotale <= 0) return null;
            Double prix = sommePrixVentePondere / quantiteVendueTotale;
            return BigDecimal.valueOf(prix)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        }
    }
    
    /**
     * Classe interne pour stocker les informations d'une vente (facture vente)
     */
    private static class VenteInfo {
        String numeroFactureVente;
        Double quantite;
        Double prixUnitaireHT;
        
        VenteInfo(String numeroFactureVente, Double quantite, Double prixUnitaireHT) {
            this.numeroFactureVente = numeroFactureVente;
            this.quantite = quantite;
            this.prixUnitaireHT = prixUnitaireHT;
        }
    }
}
