package com.bf4invest.excel;

import com.bf4invest.dto.ImportResult;
import com.bf4invest.model.TransactionBancaire;
import com.bf4invest.repository.TransactionBancaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleveBancaireImportService {
    
    private final TransactionBancaireRepository transactionRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat FRENCH_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    
    /**
     * Importe un relevé bancaire Excel et crée les transactions correspondantes
     */
    public ImportResult importReleveBancaire(MultipartFile file, Integer mois, Integer annee) {
        ImportResult result = new ImportResult();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.getErrors().add("Le fichier Excel est vide ou ne contient que l'en-tête");
                return result;
            }
            
            // Mapping intelligent des colonnes
            Map<String, Integer> columnMap = mapColumns(sheet.getRow(0));
            log.info("Colonnes mappées: {}", columnMap);
            
            if (!columnMap.containsKey("Date")) {
                result.getErrors().add("Colonne 'Date' introuvable dans le fichier");
                return result;
            }
            
            result.setTotalRows(sheet.getLastRowNum());
            
            int processedRows = 0;
            int importedCount = 0;
            
            // Supprimer les transactions existantes pour ce mois/année (éviter les doublons)
            List<TransactionBancaire> existing = transactionRepository.findByMoisAndAnnee(mois, annee);
            if (!existing.isEmpty()) {
                transactionRepository.deleteAll(existing);
                log.info("Suppression de {} transactions existantes pour {}/{}", existing.size(), mois, annee);
            }
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;
                
                try {
                    TransactionBancaire transaction = parseTransactionRow(row, columnMap, mois, annee);
                    if (transaction != null) {
                        transactionRepository.save(transaction);
                        importedCount++;
                    }
                    processedRows++;
                } catch (Exception e) {
                    log.error("Erreur lors du traitement de la ligne {}: {}", i + 1, e.getMessage(), e);
                    result.getErrors().add(String.format("Ligne %d: %s", i + 1, e.getMessage()));
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
            
            result.setSuccessCount(importedCount);
            result.setTotalRows(processedRows);
            log.info("Import terminé: {} transactions importées sur {} lignes traitées", importedCount, processedRows);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'import du relevé bancaire", e);
            result.getErrors().add("Erreur lors de l'import: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Mappe intelligemment les colonnes du fichier Excel
     */
    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        
        if (headerRow == null) {
            return columnMap;
        }
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            
            String cellValue = getCellValueAsString(cell).trim().toLowerCase();
            
            // Mapping flexible des colonnes
            if (cellValue.contains("date") && !cellValue.contains("valeur")) {
                columnMap.put("Date", i);
            } else if (cellValue.contains("valeur")) {
                columnMap.put("Valeur", i);
            } else if (cellValue.contains("libellé") || cellValue.contains("libelle") || cellValue.contains("opération") || cellValue.contains("operation")) {
                columnMap.put("Libelle", i);
            } else if (cellValue.contains("débit") || cellValue.contains("debit")) {
                columnMap.put("Debit", i);
            } else if (cellValue.contains("crédit") || cellValue.contains("credit")) {
                columnMap.put("Credit", i);
            } else if (cellValue.contains("référence") || cellValue.contains("reference")) {
                columnMap.put("Reference", i);
            }
        }
        
        return columnMap;
    }
    
    /**
     * Parse une ligne de transaction depuis le fichier Excel
     */
    private TransactionBancaire parseTransactionRow(Row row, Map<String, Integer> columnMap, Integer mois, Integer annee) {
        // Date (obligatoire)
        LocalDate dateOperation = null;
        if (columnMap.containsKey("Date")) {
            dateOperation = parseDate(row.getCell(columnMap.get("Date")));
            if (dateOperation == null) {
                throw new IllegalArgumentException("Date invalide ou manquante");
            }
        } else {
            throw new IllegalArgumentException("Colonne Date introuvable");
        }
        
        // Date Valeur (optionnelle)
        LocalDate dateValeur = null;
        if (columnMap.containsKey("Valeur")) {
            dateValeur = parseDate(row.getCell(columnMap.get("Valeur")));
        }
        
        // Libellé
        String libelle = "";
        if (columnMap.containsKey("Libelle")) {
            libelle = getCellValueAsString(row.getCell(columnMap.get("Libelle")));
        }
        
        // Débit
        Double debit = 0.0;
        if (columnMap.containsKey("Debit")) {
            debit = parseAmount(row.getCell(columnMap.get("Debit")));
            if (debit == null) debit = 0.0;
        }
        
        // Crédit
        Double credit = 0.0;
        if (columnMap.containsKey("Credit")) {
            credit = parseAmount(row.getCell(columnMap.get("Credit")));
            if (credit == null) credit = 0.0;
        }
        
        // Référence (optionnelle)
        String reference = null;
        if (columnMap.containsKey("Reference")) {
            String refValue = getCellValueAsString(row.getCell(columnMap.get("Reference")));
            if (refValue != null && !refValue.trim().isEmpty()) {
                reference = refValue.trim();
            }
        }
        
        // Ignorer les lignes où débit et crédit sont tous les deux à 0
        if ((debit == null || debit == 0.0) && (credit == null || credit == 0.0)) {
            return null;
        }
        
        return TransactionBancaire.builder()
                .dateOperation(dateOperation)
                .dateValeur(dateValeur)
                .libelle(libelle != null ? libelle.trim() : "")
                .debit(debit != null && debit > 0 ? debit : null)
                .credit(credit != null && credit > 0 ? credit : null)
                .reference(reference)
                .mapped(false)
                .mois(mois)
                .annee(annee)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Parse une date depuis une cellule Excel
     */
    private LocalDate parseDate(Cell cell) {
        if (cell == null) return null;
        
        try {
            // Essayer de lire comme date Excel
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return new java.sql.Date(date.getTime()).toLocalDate();
            }
            
            // Essayer de lire comme string et parser
            String dateStr = getCellValueAsString(cell).trim();
            if (dateStr.isEmpty()) return null;
            
            // Format DD/MM/YYYY
            return LocalDate.parse(dateStr, DATE_FORMATTER_DDMMYYYY);
            
        } catch (DateTimeParseException e) {
            log.warn("Impossible de parser la date: {}", getCellValueAsString(cell));
            return null;
        } catch (Exception e) {
            log.warn("Erreur lors du parsing de la date: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse un montant depuis une cellule Excel
     */
    private Double parseAmount(Cell cell) {
        if (cell == null) return 0.0;
        
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            }
            
            String value = getCellValueAsString(cell).trim();
            if (value.isEmpty() || value.equals("-")) return 0.0;
            
            // Remplacer les espaces (séparateurs de milliers) et virgule (décimal)
            value = value.replace(" ", "").replace(",", ".");
            
            return Double.parseDouble(value);
            
        } catch (Exception e) {
            log.warn("Impossible de parser le montant: {}", getCellValueAsString(cell));
            return 0.0;
        }
    }
    
    /**
     * Récupère la valeur d'une cellule comme string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Formater les nombres sans notation scientifique
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * Vérifie si une ligne est vide
     */
    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}

