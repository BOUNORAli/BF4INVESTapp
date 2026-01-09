package com.bf4invest.excel;

import com.bf4invest.dto.MultiPartnerSituationResponse;
import com.bf4invest.dto.PartnerSituationResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class PartnerSituationExcelExporter {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    
    static {
        NUMBER_FORMAT.setMinimumFractionDigits(2);
        NUMBER_FORMAT.setMaximumFractionDigits(2);
        NUMBER_FORMAT.setGroupingUsed(true);
    }
    
    public byte[] export(PartnerSituationResponse situation) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createSummarySheet(workbook, situation);
            createFacturesSheet(workbook, situation);
            createPrevisionsSheet(workbook, situation);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
    
    private void createSummarySheet(XSSFWorkbook workbook, PartnerSituationResponse situation) {
        Sheet sheet = workbook.createSheet("Résumé");
        
        // Styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle valueStyle = createValueStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("SITUATION FINANCIÈRE AVEC PRÉVISIONS");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        rowNum++; // Ligne vide
        
        // Informations du partenaire
        PartnerSituationResponse.PartnerInfo partner = situation.getPartnerInfo();
        Row partnerTypeRow = sheet.createRow(rowNum++);
        partnerTypeRow.createCell(0).setCellValue("Type:");
        partnerTypeRow.getCell(0).setCellStyle(labelStyle);
        partnerTypeRow.createCell(1).setCellValue(partner.getType().equals("CLIENT") ? "Client" : "Fournisseur");
        partnerTypeRow.getCell(1).setCellStyle(valueStyle);
        
        addInfoRow(sheet, rowNum++, "Nom", partner.getNom(), labelStyle, valueStyle);
        if (partner.getIce() != null) {
            addInfoRow(sheet, rowNum++, "ICE", partner.getIce(), labelStyle, valueStyle);
        }
        if (partner.getReference() != null) {
            addInfoRow(sheet, rowNum++, "Référence", partner.getReference(), labelStyle, valueStyle);
        }
        if (partner.getAdresse() != null) {
            addInfoRow(sheet, rowNum++, "Adresse", partner.getAdresse(), labelStyle, valueStyle);
        }
        if (partner.getTelephone() != null) {
            addInfoRow(sheet, rowNum++, "Téléphone", partner.getTelephone(), labelStyle, valueStyle);
        }
        if (partner.getEmail() != null) {
            addInfoRow(sheet, rowNum++, "Email", partner.getEmail(), labelStyle, valueStyle);
        }
        if (partner.getRib() != null) {
            addInfoRow(sheet, rowNum++, "RIB", partner.getRib(), labelStyle, valueStyle);
        }
        if (partner.getBanque() != null) {
            addInfoRow(sheet, rowNum++, "Banque", partner.getBanque(), labelStyle, valueStyle);
        }
        
        if (situation.getDateFrom() != null || situation.getDateTo() != null) {
            String periode = "";
            if (situation.getDateFrom() != null) {
                periode += "Du " + situation.getDateFrom().format(DATE_FORMATTER);
            }
            if (situation.getDateTo() != null) {
                periode += " au " + situation.getDateTo().format(DATE_FORMATTER);
            }
            addInfoRow(sheet, rowNum++, "Période", periode, labelStyle, valueStyle);
        }
        
        rowNum++; // Ligne vide
        
        // En-tête totaux
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("RÉCAPITULATIF");
        headerCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        
        // Totaux
        PartnerSituationResponse.Totaux totaux = situation.getTotaux();
        addTotalRow(sheet, rowNum++, "Total Facturé TTC", totaux.getTotalFactureTTC(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Facturé HT", totaux.getTotalFactureHT(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total TVA", totaux.getTotalTVA(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Payé", totaux.getTotalPaye(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Restant", totaux.getTotalRestant(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Solde", totaux.getSolde(), labelStyle, valueStyle);
        
        rowNum++; // Ligne vide
        
        // Statistiques
        Row statsHeaderRow = sheet.createRow(rowNum++);
        Cell statsHeaderCell = statsHeaderRow.createCell(0);
        statsHeaderCell.setCellValue("STATISTIQUES");
        statsHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        
        addTotalRow(sheet, rowNum++, "Nombre Factures", (double) totaux.getNombreFactures(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Factures Payées", (double) totaux.getNombreFacturesPayees(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Factures En Attente", (double) totaux.getNombreFacturesEnAttente(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Factures En Retard", (double) totaux.getNombreFacturesEnRetard(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Nombre Prévisions", (double) totaux.getNombrePrevisions(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Prévisions Réalisées", (double) totaux.getNombrePrevisionsRealisees(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Prévisions En Retard", (double) totaux.getNombrePrevisionsEnRetard(), labelStyle, valueStyle);
        
        // Ajuster la largeur des colonnes
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 15000);
        sheet.setColumnWidth(2, 20000);
        sheet.setColumnWidth(3, 15000);
    }
    
    private void createFacturesSheet(XSSFWorkbook workbook, PartnerSituationResponse situation) {
        Sheet sheet = workbook.createSheet("Factures");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        
        int rowNum = 0;
        
        // En-tête
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"N° Facture", "Date", "Échéance", "Montant TTC", "Montant HT", "TVA", "Payé", "Restant", "Statut", "Avoir"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        if (situation.getFactures() != null) {
            for (PartnerSituationResponse.FactureDetail facture : situation.getFactures()) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(facture.getNumeroFacture() != null ? facture.getNumeroFacture() : "");
                row.createCell(1).setCellValue(facture.getDateFacture() != null ? facture.getDateFacture().format(DATE_FORMATTER) : "");
                row.createCell(2).setCellValue(facture.getDateEcheance() != null ? facture.getDateEcheance().format(DATE_FORMATTER) : "");
                
                Cell ttcCell = row.createCell(3);
                ttcCell.setCellValue(facture.getMontantTTC() != null ? facture.getMontantTTC() : 0.0);
                ttcCell.setCellStyle(numberStyle);
                
                Cell htCell = row.createCell(4);
                htCell.setCellValue(facture.getMontantHT() != null ? facture.getMontantHT() : 0.0);
                htCell.setCellStyle(numberStyle);
                
                Cell tvaCell = row.createCell(5);
                tvaCell.setCellValue(facture.getMontantTVA() != null ? facture.getMontantTVA() : 0.0);
                tvaCell.setCellStyle(numberStyle);
                
                Cell payeCell = row.createCell(6);
                payeCell.setCellValue(facture.getMontantPaye() != null ? facture.getMontantPaye() : 0.0);
                payeCell.setCellStyle(numberStyle);
                
                Cell restantCell = row.createCell(7);
                restantCell.setCellValue(facture.getMontantRestant() != null ? facture.getMontantRestant() : 0.0);
                restantCell.setCellStyle(numberStyle);
                
                row.createCell(8).setCellValue(formatStatut(facture.getStatut()));
                row.createCell(9).setCellValue(facture.getEstAvoir() != null && facture.getEstAvoir() ? "Oui" : "Non");
            }
        }
        
        // Ajuster la largeur des colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 15000));
        }
    }
    
    private void createPrevisionsSheet(XSSFWorkbook workbook, PartnerSituationResponse situation) {
        Sheet sheet = workbook.createSheet("Prévisions");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        
        int rowNum = 0;
        
        // En-tête
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"N° Facture", "Date Prévue", "Montant Prévu", "Payé", "Restant", "Statut", "Notes"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        if (situation.getPrevisions() != null) {
            for (PartnerSituationResponse.PrevisionDetail prevision : situation.getPrevisions()) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(prevision.getNumeroFacture() != null ? prevision.getNumeroFacture() : "");
                row.createCell(1).setCellValue(prevision.getDatePrevue() != null ? prevision.getDatePrevue().format(DATE_FORMATTER) : "");
                
                Cell montantCell = row.createCell(2);
                montantCell.setCellValue(prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0);
                montantCell.setCellStyle(numberStyle);
                
                Cell payeCell = row.createCell(3);
                payeCell.setCellValue(prevision.getMontantPaye() != null ? prevision.getMontantPaye() : 0.0);
                payeCell.setCellStyle(numberStyle);
                
                Cell restantCell = row.createCell(4);
                restantCell.setCellValue(prevision.getMontantRestant() != null ? prevision.getMontantRestant() : 0.0);
                restantCell.setCellStyle(numberStyle);
                
                row.createCell(5).setCellValue(formatStatut(prevision.getStatut()));
                row.createCell(6).setCellValue(prevision.getNotes() != null ? prevision.getNotes() : "");
            }
        }
        
        // Ajuster la largeur des colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 15000));
        }
    }
    
    private void addInfoRow(Sheet sheet, int rowNum, String label, String value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label + ":");
        labelCell.setCellStyle(labelStyle);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "");
        valueCell.setCellStyle(valueStyle);
    }
    
    private void addTotalRow(Sheet sheet, int rowNum, String label, Double value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        Cell valueCell = row.createCell(1);
        if (value != null) {
            valueCell.setCellValue(value);
        } else {
            valueCell.setCellValue(0.0);
        }
        valueCell.setCellStyle(valueStyle);
    }
    
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private CellStyle createLabelStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createValueStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createCellStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createNumberStyle(XSSFWorkbook workbook) {
        CellStyle style = createCellStyle(workbook);
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    private String formatStatut(String statut) {
        if (statut == null) return "";
        switch (statut) {
            case "PAYEE": return "Payée";
            case "PARTIELLE": return "Partielle";
            case "EN_ATTENTE": return "En Attente";
            case "EN_RETARD": return "En Retard";
            case "PREVU": return "Prévu";
            case "REALISE": return "Réalisé";
            default: return statut;
        }
    }
    
    public byte[] exportMulti(MultiPartnerSituationResponse situation) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createMultiSummarySheet(workbook, situation);
            createMultiFacturesSheet(workbook, situation);
            createMultiPrevisionsSheet(workbook, situation);
            
            // Créer une feuille par partenaire pour le mode groupé
            if (situation.getSituationsParPartenaire() != null) {
                for (PartnerSituationResponse partnerSituation : situation.getSituationsParPartenaire()) {
                    String sheetName = partnerSituation.getPartnerInfo().getNom();
                    if (sheetName.length() > 31) {
                        sheetName = sheetName.substring(0, 31);
                    }
                    createPartnerSheet(workbook, partnerSituation, sheetName);
                }
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
    
    private void createMultiSummarySheet(XSSFWorkbook workbook, MultiPartnerSituationResponse situation) {
        Sheet sheet = workbook.createSheet("Résumé Global");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle valueStyle = createValueStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("SITUATION FINANCIÈRE MULTI-PARTENAIRES");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        rowNum++; // Ligne vide
        
        // Informations générales
        addInfoRow(sheet, rowNum++, "Nombre de partenaires", 
                String.valueOf(situation.getPartners() != null ? situation.getPartners().size() : 0), 
                labelStyle, valueStyle);
        
        if (situation.getDateFrom() != null || situation.getDateTo() != null) {
            String periode = "";
            if (situation.getDateFrom() != null) {
                periode += "Du " + situation.getDateFrom().format(DATE_FORMATTER);
            }
            if (situation.getDateTo() != null) {
                periode += " au " + situation.getDateTo().format(DATE_FORMATTER);
            }
            addInfoRow(sheet, rowNum++, "Période", periode, labelStyle, valueStyle);
        }
        
        rowNum++; // Ligne vide
        
        // En-tête totaux globaux
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("RÉCAPITULATIF GLOBAL");
        headerCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        
        // Totaux globaux
        MultiPartnerSituationResponse.TotauxGlobaux totaux = situation.getTotauxGlobaux();
        addTotalRow(sheet, rowNum++, "Total Facturé TTC", totaux.getTotalFactureTTC(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Facturé HT", totaux.getTotalFactureHT(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total TVA", totaux.getTotalTVA(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Payé", totaux.getTotalPaye(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Restant", totaux.getTotalRestant(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Solde Global", totaux.getSoldeGlobal(), labelStyle, valueStyle);
        
        rowNum++; // Ligne vide
        
        // Statistiques
        Row statsHeaderRow = sheet.createRow(rowNum++);
        Cell statsHeaderCell = statsHeaderRow.createCell(0);
        statsHeaderCell.setCellValue("STATISTIQUES");
        statsHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        
        addTotalRow(sheet, rowNum++, "Nombre Factures", (double) totaux.getNombreFactures(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Factures Payées", (double) totaux.getNombreFacturesPayees(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Factures En Attente", (double) totaux.getNombreFacturesEnAttente(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Factures En Retard", (double) totaux.getNombreFacturesEnRetard(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Nombre Prévisions", (double) totaux.getNombrePrevisions(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Prévisions Réalisées", (double) totaux.getNombrePrevisionsRealisees(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Prévisions En Retard", (double) totaux.getNombrePrevisionsEnRetard(), labelStyle, valueStyle);
        
        // Ajuster la largeur des colonnes
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 15000);
        sheet.setColumnWidth(2, 20000);
        sheet.setColumnWidth(3, 15000);
    }
    
    private void createMultiFacturesSheet(XSSFWorkbook workbook, MultiPartnerSituationResponse situation) {
        Sheet sheet = workbook.createSheet("Factures Consolidées");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        
        int rowNum = 0;
        
        // En-tête
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Partenaire", "N° Facture", "Date", "Échéance", "Montant TTC", "Montant HT", "TVA", "Payé", "Restant", "Statut", "Avoir"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        if (situation.getFacturesConsolidees() != null) {
            for (MultiPartnerSituationResponse.FactureDetailWithPartner factureWithPartner : situation.getFacturesConsolidees()) {
                PartnerSituationResponse.FactureDetail facture = factureWithPartner.getFacture();
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(factureWithPartner.getPartnerNom() != null ? factureWithPartner.getPartnerNom() : "");
                row.createCell(1).setCellValue(facture.getNumeroFacture() != null ? facture.getNumeroFacture() : "");
                row.createCell(2).setCellValue(facture.getDateFacture() != null ? facture.getDateFacture().format(DATE_FORMATTER) : "");
                row.createCell(3).setCellValue(facture.getDateEcheance() != null ? facture.getDateEcheance().format(DATE_FORMATTER) : "");
                
                Cell ttcCell = row.createCell(4);
                ttcCell.setCellValue(facture.getMontantTTC() != null ? facture.getMontantTTC() : 0.0);
                ttcCell.setCellStyle(numberStyle);
                
                Cell htCell = row.createCell(5);
                htCell.setCellValue(facture.getMontantHT() != null ? facture.getMontantHT() : 0.0);
                htCell.setCellStyle(numberStyle);
                
                Cell tvaCell = row.createCell(6);
                tvaCell.setCellValue(facture.getMontantTVA() != null ? facture.getMontantTVA() : 0.0);
                tvaCell.setCellStyle(numberStyle);
                
                Cell payeCell = row.createCell(7);
                payeCell.setCellValue(facture.getMontantPaye() != null ? facture.getMontantPaye() : 0.0);
                payeCell.setCellStyle(numberStyle);
                
                Cell restantCell = row.createCell(8);
                restantCell.setCellValue(facture.getMontantRestant() != null ? facture.getMontantRestant() : 0.0);
                restantCell.setCellStyle(numberStyle);
                
                row.createCell(9).setCellValue(formatStatut(facture.getStatut()));
                row.createCell(10).setCellValue(facture.getEstAvoir() != null && facture.getEstAvoir() ? "Oui" : "Non");
            }
        }
        
        // Ajuster la largeur des colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 15000));
        }
    }
    
    private void createMultiPrevisionsSheet(XSSFWorkbook workbook, MultiPartnerSituationResponse situation) {
        Sheet sheet = workbook.createSheet("Prévisions Consolidées");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        
        int rowNum = 0;
        
        // En-tête
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Partenaire", "N° Facture", "Date Prévue", "Montant Prévu", "Payé", "Restant", "Statut", "Notes"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        if (situation.getPrevisionsConsolidees() != null) {
            for (MultiPartnerSituationResponse.PrevisionDetailWithPartner previsionWithPartner : situation.getPrevisionsConsolidees()) {
                PartnerSituationResponse.PrevisionDetail prevision = previsionWithPartner.getPrevision();
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(previsionWithPartner.getPartnerNom() != null ? previsionWithPartner.getPartnerNom() : "");
                row.createCell(1).setCellValue(prevision.getNumeroFacture() != null ? prevision.getNumeroFacture() : "");
                row.createCell(2).setCellValue(prevision.getDatePrevue() != null ? prevision.getDatePrevue().format(DATE_FORMATTER) : "");
                
                Cell montantCell = row.createCell(3);
                montantCell.setCellValue(prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0);
                montantCell.setCellStyle(numberStyle);
                
                Cell payeCell = row.createCell(4);
                payeCell.setCellValue(prevision.getMontantPaye() != null ? prevision.getMontantPaye() : 0.0);
                payeCell.setCellStyle(numberStyle);
                
                Cell restantCell = row.createCell(5);
                restantCell.setCellValue(prevision.getMontantRestant() != null ? prevision.getMontantRestant() : 0.0);
                restantCell.setCellStyle(numberStyle);
                
                row.createCell(6).setCellValue(formatStatut(prevision.getStatut()));
                row.createCell(7).setCellValue(prevision.getNotes() != null ? prevision.getNotes() : "");
            }
        }
        
        // Ajuster la largeur des colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 15000));
        }
    }
    
    private void createPartnerSheet(XSSFWorkbook workbook, PartnerSituationResponse situation, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle valueStyle = createValueStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("SITUATION - " + situation.getPartnerInfo().getNom().toUpperCase());
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        rowNum++; // Ligne vide
        
        // Informations du partenaire
        PartnerSituationResponse.PartnerInfo partner = situation.getPartnerInfo();
        addInfoRow(sheet, rowNum++, "Type", partner.getType().equals("CLIENT") ? "Client" : "Fournisseur", labelStyle, valueStyle);
        addInfoRow(sheet, rowNum++, "Nom", partner.getNom(), labelStyle, valueStyle);
        if (partner.getIce() != null) {
            addInfoRow(sheet, rowNum++, "ICE", partner.getIce(), labelStyle, valueStyle);
        }
        if (partner.getReference() != null) {
            addInfoRow(sheet, rowNum++, "Référence", partner.getReference(), labelStyle, valueStyle);
        }
        
        rowNum++; // Ligne vide
        
        // En-tête totaux
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("RÉCAPITULATIF");
        headerCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        
        // Totaux
        PartnerSituationResponse.Totaux totaux = situation.getTotaux();
        addTotalRow(sheet, rowNum++, "Total Facturé TTC", totaux.getTotalFactureTTC(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Payé", totaux.getTotalPaye(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Total Restant", totaux.getTotalRestant(), labelStyle, valueStyle);
        addTotalRow(sheet, rowNum++, "Solde", totaux.getSolde(), labelStyle, valueStyle);
        
        // Ajuster la largeur des colonnes
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 15000);
    }
}

