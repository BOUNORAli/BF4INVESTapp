package com.bf4invest.excel;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.service.CalculComptableService;
import com.bf4invest.service.SoldeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private final CalculComptableService calculComptableService;
    private final SoldeService soldeService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    
    /**
     * Classe interne pour représenter une ligne de transaction dans l'export Excel
     */
    private static class TransactionRow {
        String affectation; // BC reference
        String releveBC; // Abréviation client/fournisseur
        String abnCliFou; // "C" or "F"
        String bm; // "BM" or "C"
        String clientFournisseur; // Nom complet
        LocalDate date;
        String source; // "Facture", "Paiement", "LOYER"
        String numeroDocument;
        String refFactureE;
        Double totalTTC;
        Double apresRgTTC;
        Double tauxTVA; // En pourcentage (20.0 pour 20%)
        Double tva;
        String modePaiement;
        String commentaire;
        Integer annee;
        Integer mois;
        Double soldeBanque; // Cumulative
        Double totalTTCApresRG; // Redondant
        Double htRg;
        Double rg;
        Double ht;
        Double tva2; // Redondant
        Double factureHTVG;
        Double htPaye;
        Double tvaPaye;
        Double bilan; // Cumulative
        Double ca; // Cumulative (only for sales invoices)
        
        // Type de transaction pour le tri et le calcul
        String transactionType; // "FACTURE_VENTE", "FACTURE_ACHAT", "PAIEMENT"
    }
    
    public byte[] exportBCsToExcel(List<BandeCommande> bcs) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export BCs");
            
            // Créer un cache des clients et fournisseurs
            Map<String, Client> clientsMap = clientRepository.findAll().stream()
                    .collect(Collectors.toMap(Client::getId, c -> c));
            Map<String, Supplier> suppliersMap = supplierRepository.findAll().stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s));
            
            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle currencyNegativeStyle = createCurrencyNegativeStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle defaultStyle = createDefaultStyle(workbook);
            CellStyle paymentRowStyle = createPaymentRowStyle(workbook);
            
            // Créer l'en-tête avec toutes les colonnes
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "AFFECTATION",
                "RELEVE BC",
                "ABN/CLI/FOU",
                "BM",
                "CLIENT / Fourn.",
                "DATE",
                "SOURCE",
                "N° DOCUMENT",
                "REF. FACTURE E",
                "TOTAL TTC",
                "APRES RG TTC",
                "TAUX TVA",
                "TVA",
                "MODE de paiement",
                "COMMENTAIRE",
                "année",
                "mois",
                "SOLDE BANQUE",
                "TOTAL TTC APRES RG",
                "HT RG",
                "RG",
                "HT",
                "TVA",
                "FACTURE HT VG",
                "HT PAYE",
                "TVA PAYE",
                "bilan",
                "CA",
                "PAUSE"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Collecter toutes les transactions
            List<TransactionRow> allTransactions = new ArrayList<>();
            
            for (BandeCommande bc : bcs) {
                String bcReference = bc.getNumeroBC() != null ? bc.getNumeroBC() : "";
                
                // Récupérer les factures de vente liées
                List<FactureVente> facturesVente = factureVenteRepository.findByBandeCommandeId(bc.getId());
                for (FactureVente fv : facturesVente) {
                    // S'assurer que tous les champs sont calculés
                    calculComptableService.calculerFactureVente(fv);
                    
                    Client client = fv.getClientId() != null ? clientsMap.get(fv.getClientId()) : null;
                    TransactionRow row = createTransactionRowFromFactureVente(fv, bcReference, client);
                    allTransactions.add(row);
                }
                
                // Récupérer les factures d'achat liées
                List<FactureAchat> facturesAchat = factureAchatRepository.findByBandeCommandeId(bc.getId());
                for (FactureAchat fa : facturesAchat) {
                    // S'assurer que tous les champs sont calculés
                    calculComptableService.calculerFactureAchat(fa);
                    
                    Supplier supplier = fa.getFournisseurId() != null ? suppliersMap.get(fa.getFournisseurId()) : null;
                    TransactionRow row = createTransactionRowFromFactureAchat(fa, bcReference, supplier);
                    allTransactions.add(row);
                }
                
                // Récupérer tous les paiements liés aux factures de ce BC
                for (FactureVente fv : facturesVente) {
                    List<Paiement> paiements = paiementRepository.findByFactureVenteId(fv.getId());
                    for (Paiement p : paiements) {
                        calculComptableService.calculerPaiement(p);
                        Client client = fv.getClientId() != null ? clientsMap.get(fv.getClientId()) : null;
                        TransactionRow row = createTransactionRowFromPaiement(p, bcReference, client, "C", fv.getNumeroFactureVente());
                        allTransactions.add(row);
                    }
                }
                
                for (FactureAchat fa : facturesAchat) {
                    List<Paiement> paiements = paiementRepository.findByFactureAchatId(fa.getId());
                    for (Paiement p : paiements) {
                        calculComptableService.calculerPaiement(p);
                        Supplier supplier = fa.getFournisseurId() != null ? suppliersMap.get(fa.getFournisseurId()) : null;
                        TransactionRow row = createTransactionRowFromPaiement(p, bcReference, supplier, "F", fa.getNumeroFactureAchat());
                        allTransactions.add(row);
                    }
                }
            }
            
            // Trier toutes les transactions par date
            allTransactions.sort(Comparator.comparing(t -> t.date != null ? t.date : LocalDate.MIN));
            
            // Calculer les valeurs cumulatives
            // Récupérer le solde initial depuis SoldeGlobal
            double soldeInitial = soldeService.getSoldeGlobal()
                    .map(sg -> sg.getSoldeInitial() != null ? sg.getSoldeInitial() : 0.0)
                    .orElse(0.0);
            
            // Calculer le solde initial réel en remontant dans le temps
            // Le solde actuel = solde initial + tous les paiements clients - tous les paiements fournisseurs
            // Donc solde initial = solde actuel - tous les paiements clients + tous les paiements fournisseurs
            // Mais pour l'export, on veut partir du solde initial et recalculer en avançant
            // Donc on va partir du solde initial et appliquer chaque paiement dans l'ordre
            double soldeBanqueCumul = soldeInitial;
            double bilanCumul = 0.0;
            double caCumul = 0.0;
            
            // Remplir les données dans Excel
            int rowNum = 1;
            for (TransactionRow t : allTransactions) {
                Row row = sheet.createRow(rowNum++);
                
                // Appliquer le style de ligne paiement si c'est un paiement
                CellStyle rowStyle = "PAIEMENT".equals(t.transactionType) ? paymentRowStyle : defaultStyle;
                
                int colIndex = 0;
                
                // AFFECTATION
                setCellValue(row, colIndex++, t.affectation, rowStyle);
                
                // RELEVE BC
                setCellValue(row, colIndex++, t.releveBC, rowStyle);
                
                // ABN/CLI/FOU
                setCellValue(row, colIndex++, t.abnCliFou, rowStyle);
                
                // BM
                setCellValue(row, colIndex++, t.bm, rowStyle);
                
                // CLIENT / Fourn.
                setCellValue(row, colIndex++, t.clientFournisseur, rowStyle);
                
                // DATE
                Cell dateCell = row.createCell(colIndex++);
                if (t.date != null) {
                    dateCell.setCellValue(t.date.format(DATE_FORMATTER));
                } else {
                    dateCell.setCellValue("");
                }
                dateCell.setCellStyle(dateStyle);
                
                // SOURCE
                setCellValue(row, colIndex++, t.source, rowStyle);
                
                // N° DOCUMENT
                setCellValue(row, colIndex++, t.numeroDocument, rowStyle);
                
                // REF. FACTURE E
                setCellValue(row, colIndex++, t.refFactureE, rowStyle);
                
                // TOTAL TTC
                setCellValue(row, colIndex++, t.totalTTC, currencyStyle, currencyNegativeStyle);
                
                // APRES RG TTC
                setCellValue(row, colIndex++, t.apresRgTTC, currencyStyle, currencyNegativeStyle);
                
                // TAUX TVA
                Cell tvaRateCell = row.createCell(colIndex++);
                if (t.tauxTVA != null) {
                    tvaRateCell.setCellValue(t.tauxTVA / 100.0); // Format Excel pourcentage
                    tvaRateCell.setCellStyle(percentStyle);
                } else {
                    tvaRateCell.setCellValue("");
                    tvaRateCell.setCellStyle(rowStyle);
                }
                
                // TVA
                setCellValue(row, colIndex++, t.tva, currencyStyle, currencyNegativeStyle);
                
                // MODE de paiement
                setCellValue(row, colIndex++, t.modePaiement, rowStyle);
                
                // COMMENTAIRE
                setCellValue(row, colIndex++, t.commentaire, rowStyle);
                
                // année
                setCellValue(row, colIndex++, t.annee != null ? t.annee.doubleValue() : null, defaultStyle);
                
                // mois
                setCellValue(row, colIndex++, t.mois != null ? t.mois.doubleValue() : null, defaultStyle);
                
                // SOLDE BANQUE (cumulatif)
                // Mettre à jour le solde cumulatif
                if ("PAIEMENT".equals(t.transactionType)) {
                    if ("C".equals(t.abnCliFou)) {
                        // Paiement client : augmente le solde
                        soldeBanqueCumul += (t.totalTTC != null ? t.totalTTC : 0.0);
                    } else if ("F".equals(t.abnCliFou)) {
                        // Paiement fournisseur : diminue le solde
                        soldeBanqueCumul -= (t.totalTTC != null ? t.totalTTC : 0.0);
                    }
                }
                t.soldeBanque = soldeBanqueCumul;
                setCellValue(row, colIndex++, soldeBanqueCumul, currencyStyle, currencyNegativeStyle);
                
                // TOTAL TTC APRES RG (redondant)
                setCellValue(row, colIndex++, t.totalTTCApresRG, currencyStyle, currencyNegativeStyle);
                
                // HT RG
                setCellValue(row, colIndex++, t.htRg, currencyStyle, currencyNegativeStyle);
                
                // RG
                setCellValue(row, colIndex++, t.rg, currencyStyle, currencyNegativeStyle);
                
                // HT
                setCellValue(row, colIndex++, t.ht, currencyStyle, currencyNegativeStyle);
                
                // TVA (redondant)
                setCellValue(row, colIndex++, t.tva2, currencyStyle, currencyNegativeStyle);
                
                // FACTURE HT VG
                setCellValue(row, colIndex++, t.factureHTVG, currencyStyle, currencyNegativeStyle);
                
                // HT PAYE
                setCellValue(row, colIndex++, t.htPaye, currencyStyle, currencyNegativeStyle);
                
                // TVA PAYE
                setCellValue(row, colIndex++, t.tvaPaye, currencyStyle, currencyNegativeStyle);
                
                // bilan (cumulatif)
                if (t.bilan != null) {
                    bilanCumul += t.bilan;
                }
                t.bilan = bilanCumul;
                setCellValue(row, colIndex++, bilanCumul, currencyStyle, currencyNegativeStyle);
                
                // CA (cumulatif, uniquement pour factures vente)
                if ("FACTURE_VENTE".equals(t.transactionType) && t.ht != null) {
                    caCumul += t.ht;
                }
                t.ca = "FACTURE_VENTE".equals(t.transactionType) ? caCumul : null;
                setCellValue(row, colIndex++, "FACTURE_VENTE".equals(t.transactionType) ? caCumul : null, currencyStyle, currencyNegativeStyle);
                
                // PAUSE (vide)
                setCellValue(row, colIndex++, "", rowStyle);
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }
            
            // Créer un ByteArrayOutputStream pour retourner les bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
    
    /**
     * Crée une TransactionRow à partir d'une FactureVente
     */
    private TransactionRow createTransactionRowFromFactureVente(FactureVente fv, String bcReference, Client client) {
        TransactionRow row = new TransactionRow();
        row.affectation = bcReference;
        row.releveBC = client != null && client.getReferenceClient() != null ? client.getReferenceClient() : 
                       (client != null ? extractAbbreviation(client.getNom()) : "");
        row.abnCliFou = "C";
        row.bm = "BM"; // À déterminer selon la logique métier
        row.clientFournisseur = client != null ? client.getNom() : "Inconnu";
        row.date = fv.getDateFacture();
        row.source = "Facture";
        row.numeroDocument = fv.getNumeroFactureVente();
        row.refFactureE = fv.getNumeroFactureVente(); // Ou référence externe si disponible
        row.totalTTC = fv.getTotalTTC();
        row.apresRgTTC = fv.getTotalTTCApresRG();
        row.tauxTVA = fv.getTvaRate() != null ? fv.getTvaRate() * 100 : 20.0; // Convertir en pourcentage
        row.tva = fv.getTotalTVA();
        row.modePaiement = fv.getModePaiement();
        row.commentaire = ""; // À remplir si nécessaire
        row.annee = fv.getDateFacture() != null ? fv.getDateFacture().getYear() : null;
        row.mois = fv.getDateFacture() != null ? fv.getDateFacture().getMonthValue() : null;
        row.totalTTCApresRG = fv.getTotalTTCApresRG();
        row.htRg = fv.getFactureHT_YC_RG();
        row.rg = fv.getRgTTC();
        row.ht = fv.getTotalHT();
        row.tva2 = fv.getTotalTVA();
        row.factureHTVG = fv.getFactureHT_YC_RG();
        row.htPaye = fv.getHtPaye();
        row.tvaPaye = fv.getTvaPaye();
        row.bilan = fv.getBilan();
        row.ca = null; // Sera calculé cumulativement
        row.transactionType = "FACTURE_VENTE";
        return row;
    }
    
    /**
     * Crée une TransactionRow à partir d'une FactureAchat
     */
    private TransactionRow createTransactionRowFromFactureAchat(FactureAchat fa, String bcReference, Supplier supplier) {
        TransactionRow row = new TransactionRow();
        row.affectation = bcReference;
        row.releveBC = supplier != null && supplier.getReferenceFournisseur() != null ? supplier.getReferenceFournisseur() : 
                       (supplier != null ? extractAbbreviation(supplier.getNom()) : "");
        row.abnCliFou = "F";
        row.bm = "BM"; // À déterminer selon la logique métier
        row.clientFournisseur = supplier != null ? supplier.getNom() : "Inconnu";
        row.date = fa.getDateFacture();
        row.source = "Facture";
        row.numeroDocument = fa.getNumeroFactureAchat();
        row.refFactureE = fa.getNumeroFactureAchat(); // Ou référence externe si disponible
        row.totalTTC = fa.getTotalTTC();
        row.apresRgTTC = fa.getTotalTTCApresRG();
        row.tauxTVA = fa.getTvaRate() != null ? fa.getTvaRate() * 100 : 20.0; // Convertir en pourcentage
        row.tva = fa.getTotalTVA();
        row.modePaiement = fa.getModePaiement();
        row.commentaire = ""; // À remplir si nécessaire
        row.annee = fa.getDateFacture() != null ? fa.getDateFacture().getYear() : null;
        row.mois = fa.getDateFacture() != null ? fa.getDateFacture().getMonthValue() : null;
        row.totalTTCApresRG = fa.getTotalTTCApresRG();
        row.htRg = fa.getFactureHT_YC_RG();
        row.rg = fa.getRgTTC();
        row.ht = fa.getTotalHT();
        row.tva2 = fa.getTotalTVA();
        row.factureHTVG = fa.getFactureHT_YC_RG();
        row.htPaye = fa.getHtPaye();
        row.tvaPaye = fa.getTvaPaye();
        row.bilan = fa.getBilan();
        row.ca = null; // Pas de CA pour les factures d'achat
        row.transactionType = "FACTURE_ACHAT";
        return row;
    }
    
    /**
     * Crée une TransactionRow à partir d'un Paiement
     */
    private TransactionRow createTransactionRowFromPaiement(Paiement p, String bcReference, Object partenaire, String type, String refFacture) {
        TransactionRow row = new TransactionRow();
        row.affectation = bcReference;
        
        if (partenaire instanceof Client) {
            Client client = (Client) partenaire;
            row.releveBC = client.getReferenceClient() != null ? client.getReferenceClient() : extractAbbreviation(client.getNom());
            row.clientFournisseur = client.getNom();
        } else if (partenaire instanceof Supplier) {
            Supplier supplier = (Supplier) partenaire;
            row.releveBC = supplier.getReferenceFournisseur() != null ? supplier.getReferenceFournisseur() : extractAbbreviation(supplier.getNom());
            row.clientFournisseur = supplier.getNom();
        } else {
            row.releveBC = "";
            row.clientFournisseur = "Inconnu";
        }
        
        row.abnCliFou = type;
        row.bm = "BM";
        row.date = p.getDate();
        row.source = "Paiement";
        row.numeroDocument = p.getReference() != null ? p.getReference() : "";
        row.refFactureE = refFacture;
        row.totalTTC = p.getMontant();
        row.apresRgTTC = p.getTotalPaiementTTC();
        row.tauxTVA = p.getTvaRate() != null ? p.getTvaRate() * 100 : 20.0;
        row.tva = p.getTvaPaye();
        row.modePaiement = p.getMode();
        row.commentaire = p.getNotes();
        row.annee = p.getDate() != null ? p.getDate().getYear() : null;
        row.mois = p.getDate() != null ? p.getDate().getMonthValue() : null;
        row.totalTTCApresRG = p.getTotalPaiementTTC();
        row.htRg = null; // Pas applicable pour les paiements
        row.rg = null; // Pas applicable pour les paiements
        row.ht = p.getHtPaye();
        row.tva2 = p.getTvaPaye();
        row.factureHTVG = null; // Pas applicable pour les paiements
        row.htPaye = p.getHtPaye();
        row.tvaPaye = p.getTvaPaye();
        row.bilan = null; // Pas applicable pour les paiements
        row.ca = null; // Pas de CA pour les paiements
        row.transactionType = "PAIEMENT";
        return row;
    }
    
    /**
     * Extrait une abréviation (3 premières lettres) d'un nom
     */
    private String extractAbbreviation(String nom) {
        if (nom == null || nom.isEmpty()) {
            return "";
        }
        String cleaned = nom.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        return cleaned.length() >= 3 ? cleaned.substring(0, 3) : cleaned;
    }
    
    /**
     * Helper pour définir une valeur de cellule avec style
     */
    private void setCellValue(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
    
    /**
     * Helper pour définir une valeur numérique de cellule avec style
     */
    private void setCellValue(Row row, int colIndex, Double value, CellStyle positiveStyle, CellStyle negativeStyle) {
        Cell cell = row.createCell(colIndex);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(value < 0 ? negativeStyle : positiveStyle);
        } else {
            cell.setCellValue("");
            cell.setCellStyle(positiveStyle);
        }
    }
    
    /**
     * Helper pour définir une valeur numérique de cellule avec un seul style
     */
    private void setCellValue(Row row, int colIndex, Double value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setCellValue("");
        }
        cell.setCellStyle(style);
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
    
    private CellStyle createCurrencyNegativeStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00 \"MAD\""));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        return style;
    }
    
    private CellStyle createPaymentRowStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        return style;
    }
}

