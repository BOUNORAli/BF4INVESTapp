package com.bf4invest.excel;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import com.bf4invest.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {
    
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final PaiementRepository paiementRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    
    public byte[] exportBCsToExcel(List<BandeCommande> bcs) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export BCs");
            
            // Créer un cache des clients et fournisseurs pour éviter les requêtes répétées
            Map<String, Client> clientsMap = clientRepository.findAll().stream()
                    .collect(Collectors.toMap(Client::getId, c -> c));
            Map<String, Supplier> suppliersMap = supplierRepository.findAll().stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s));
            
            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle defaultStyle = createDefaultStyle(workbook);
            
            // Créer l'en-tête
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "N° BC",
                "Date",
                "Client",
                "Fournisseur",
                "Total Achat HT",
                "Total Vente HT",
                "Marge (MAD)",
                "Marge (%)",
                "Statut",
                "Reste à Payer Factures Vente",
                "Reste à Payer Factures Achat",
                "Total Reste à Payer"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Remplir les données
            int rowNum = 1;
            for (BandeCommande bc : bcs) {
                Row row = sheet.createRow(rowNum++);
                
                // N° BC
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(bc.getNumeroBC() != null ? bc.getNumeroBC() : "");
                cell0.setCellStyle(defaultStyle);
                
                // Date
                Cell cell1 = row.createCell(1);
                if (bc.getDateBC() != null) {
                    cell1.setCellValue(bc.getDateBC().format(DATE_FORMATTER));
                } else {
                    cell1.setCellValue("");
                }
                cell1.setCellStyle(dateStyle);
                
                // Client
                Cell cell2 = row.createCell(2);
                String clientName = "Inconnu";
                if (bc.getClientId() != null && clientsMap.containsKey(bc.getClientId())) {
                    clientName = clientsMap.get(bc.getClientId()).getNom();
                }
                cell2.setCellValue(clientName);
                cell2.setCellStyle(defaultStyle);
                
                // Fournisseur
                Cell cell3 = row.createCell(3);
                String supplierName = "Inconnu";
                if (bc.getFournisseurId() != null && suppliersMap.containsKey(bc.getFournisseurId())) {
                    supplierName = suppliersMap.get(bc.getFournisseurId()).getNom();
                }
                cell3.setCellValue(supplierName);
                cell3.setCellStyle(defaultStyle);
                
                // Total Achat HT
                Cell cell4 = row.createCell(4);
                double totalAchatHT = bc.getTotalAchatHT() != null ? bc.getTotalAchatHT() : 0.0;
                cell4.setCellValue(totalAchatHT);
                cell4.setCellStyle(currencyStyle);
                
                // Total Vente HT
                Cell cell5 = row.createCell(5);
                double totalVenteHT = bc.getTotalVenteHT() != null ? bc.getTotalVenteHT() : 0.0;
                cell5.setCellValue(totalVenteHT);
                cell5.setCellStyle(currencyStyle);
                
                // Marge (MAD)
                Cell cell6 = row.createCell(6);
                double margeTotale = bc.getMargeTotale() != null ? bc.getMargeTotale() : (totalVenteHT - totalAchatHT);
                cell6.setCellValue(margeTotale);
                cell6.setCellStyle(currencyStyle);
                
                // Marge (%)
                Cell cell7 = row.createCell(7);
                double margePourcent = bc.getMargePourcentage() != null ? bc.getMargePourcentage() : 
                        (totalAchatHT > 0 ? ((totalVenteHT - totalAchatHT) / totalAchatHT * 100) : 0.0);
                cell7.setCellValue(margePourcent / 100.0); // Format Excel pourcentage (0.15 = 15%)
                cell7.setCellStyle(percentStyle);
                
                // Statut
                Cell cell8 = row.createCell(8);
                String status = "Brouillon";
                if (bc.getEtat() != null) {
                    switch (bc.getEtat()) {
                        case "envoyee":
                            status = "Envoyée";
                            break;
                        case "complete":
                            status = "Validée";
                            break;
                        default:
                            status = "Brouillon";
                    }
                }
                cell8.setCellValue(status);
                cell8.setCellStyle(defaultStyle);
                
                // Calculer le reste à payer pour les factures liées à ce BC
                double resteAPayerVente = 0.0;
                double resteAPayerAchat = 0.0;
                
                // Factures de vente liées à ce BC
                List<FactureVente> facturesVente = factureVenteRepository.findByBandeCommandeId(bc.getId());
                for (FactureVente fv : facturesVente) {
                    double totalTTC = fv.getTotalTTC() != null ? fv.getTotalTTC() : 0.0;
                    // Calculer le total des paiements pour cette facture
                    List<Paiement> paiementsVente = paiementRepository.findByFactureVenteId(fv.getId());
                    double totalPaye = paiementsVente.stream()
                            .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                            .sum();
                    double reste = totalTTC - totalPaye;
                    if (reste > 0) {
                        resteAPayerVente += reste;
                    }
                }
                
                // Factures d'achat liées à ce BC
                List<FactureAchat> facturesAchat = factureAchatRepository.findByBandeCommandeId(bc.getId());
                for (FactureAchat fa : facturesAchat) {
                    double totalTTC = fa.getTotalTTC() != null ? fa.getTotalTTC() : 0.0;
                    // Calculer le total des paiements pour cette facture
                    List<Paiement> paiementsAchat = paiementRepository.findByFactureAchatId(fa.getId());
                    double totalPaye = paiementsAchat.stream()
                            .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                            .sum();
                    double reste = totalTTC - totalPaye;
                    if (reste > 0) {
                        resteAPayerAchat += reste;
                    }
                }
                
                // Reste à payer Factures Vente
                Cell cell9 = row.createCell(9);
                cell9.setCellValue(resteAPayerVente);
                cell9.setCellStyle(currencyStyle);
                
                // Reste à payer Factures Achat
                Cell cell10 = row.createCell(10);
                cell10.setCellValue(resteAPayerAchat);
                cell10.setCellStyle(currencyStyle);
                
                // Total Reste à Payer
                Cell cell11 = row.createCell(11);
                double totalResteAPayer = resteAPayerVente + resteAPayerAchat;
                cell11.setCellValue(totalResteAPayer);
                cell11.setCellStyle(currencyStyle);
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Ajouter un peu de padding
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }
            
            // Créer un ByteArrayOutputStream pour retourner les bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
    
    private CellStyle createHeaderStyle(Workbook workbook) {
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
        return style;
    }
    
    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00 \"MAD\""));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private CellStyle createDefaultStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}

