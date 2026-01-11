package com.bf4invest.excel;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.Client;
import com.bf4invest.model.Charge;
import com.bf4invest.model.ClientVente;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.HistoriqueSolde;
import com.bf4invest.model.LigneAchat;
import com.bf4invest.model.LigneVente;
import com.bf4invest.model.Paiement;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.PaiementRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.service.CalculComptableService;
import com.bf4invest.service.CompanyInfoService;
import com.bf4invest.service.SoldeService;
import com.bf4invest.service.TVAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final TVAService tvaService;
    private final CompanyInfoService companyInfoService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    
    /**
     * Classe interne pour représenter une ligne du format import
     */
    private static class ImportFormatRow {
        String numeroBC;
        LocalDate dateBC;
        String numeroFactureFournisseur;
        LocalDate dateFactureAchat;
        String numeroFactureVente;
        LocalDate dateFactureVente;
        String ice;
        String fournisseur;
        String client;
        String numeroArticle;
        String designation;
        String unite;
        Double quantiteBC;
        Double quantiteLivree;
        Double prixAchatUnitaireHT;
        Double prixAchatTotalHT;
        Double tauxTVA;
        Double prixAchatUnitaireTTC;
        Double prixAchatTTC;
        Double factureAchatTTC;
        Double prixVenteUnitaireHT;
        Double prixVenteUnitaireTTC;
        Double factureVenteTTC;
        Double margeUnitaireTTC;
    }
    
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
                
                // Récupérer les factures de vente liées (chercher par ID ET par référence)
                List<FactureVente> facturesVente = new ArrayList<>();
                
                // Chercher par bandeCommandeId (ID MongoDB)
                List<FactureVente> fvById = factureVenteRepository.findByBandeCommandeId(bc.getId());
                facturesVente.addAll(fvById);
                
                // Chercher aussi par bcReference (référence textuelle) pour éviter les factures manquantes
                if (bcReference != null && !bcReference.isEmpty()) {
                    List<FactureVente> fvByRef = factureVenteRepository.findByBcReference(bcReference);
                    // Ajouter seulement les factures qui ne sont pas déjà dans la liste (éviter doublons)
                    for (FactureVente fv : fvByRef) {
                        if (facturesVente.stream().noneMatch(existing -> existing.getId().equals(fv.getId()))) {
                            facturesVente.add(fv);
                        }
                    }
                }
                
                for (FactureVente fv : facturesVente) {
                    // S'assurer que tous les champs sont calculés
                    calculComptableService.calculerFactureVente(fv);
                    
                    Client client = fv.getClientId() != null ? clientsMap.get(fv.getClientId()) : null;
                    TransactionRow row = createTransactionRowFromFactureVente(fv, bcReference, client);
                    allTransactions.add(row);
                }
                
                // Récupérer les factures d'achat liées (chercher par ID ET par référence)
                List<FactureAchat> facturesAchat = new ArrayList<>();
                
                // Chercher par bandeCommandeId (ID MongoDB)
                List<FactureAchat> faById = factureAchatRepository.findByBandeCommandeId(bc.getId());
                facturesAchat.addAll(faById);
                
                // Chercher aussi par bcReference (référence textuelle)
                if (bcReference != null && !bcReference.isEmpty()) {
                    List<FactureAchat> faByRef = factureAchatRepository.findByBcReference(bcReference);
                    // Ajouter seulement les factures qui ne sont pas déjà dans la liste (éviter doublons)
                    for (FactureAchat fa : faByRef) {
                        if (facturesAchat.stream().noneMatch(existing -> existing.getId().equals(fa.getId()))) {
                            facturesAchat.add(fa);
                        }
                    }
                }
                
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
    
    /**
     * Exporte l'historique de trésorerie en Excel
     */
    public byte[] exportHistoriqueTresorerie(List<HistoriqueSolde> historique) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Historique Trésorerie");
            
            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle defaultStyle = createDefaultStyle(workbook);
            
            // En-têtes
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Date", "Type", "Partenaire", "Référence", "Montant", 
                "Solde Global Avant", "Solde Global Après", 
                "Solde Partenaire Avant", "Solde Partenaire Après", "Description"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Données
            int rowNum = 1;
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            
            for (HistoriqueSolde item : historique) {
                Row row = sheet.createRow(rowNum++);
                
                // Date
                Cell dateCell = row.createCell(0);
                if (item.getDate() != null) {
                    LocalDateTime dateTime = item.getDate();
                    dateCell.setCellValue(dateTime.format(dateTimeFormatter));
                } else {
                    dateCell.setCellValue("-");
                }
                dateCell.setCellStyle(dateStyle);
                
                // Type
                Cell typeCell = row.createCell(1);
                typeCell.setCellValue(getTypeLabel(item.getType()));
                typeCell.setCellStyle(defaultStyle);
                
                // Partenaire
                Cell partenaireCell = row.createCell(2);
                partenaireCell.setCellValue(item.getPartenaireNom() != null ? item.getPartenaireNom() : "-");
                partenaireCell.setCellStyle(defaultStyle);
                
                // Référence
                Cell refCell = row.createCell(3);
                refCell.setCellValue(item.getReferenceNumero() != null ? item.getReferenceNumero() : "-");
                refCell.setCellStyle(defaultStyle);
                
                // Montant
                Cell montantCell = row.createCell(4);
                if (item.getMontant() != null) {
                    montantCell.setCellValue(item.getMontant());
                } else {
                    montantCell.setCellValue(0.0);
                }
                montantCell.setCellStyle(currencyStyle);
                
                // Solde Global Avant
                Cell soldeAvantCell = row.createCell(5);
                if (item.getSoldeGlobalAvant() != null) {
                    soldeAvantCell.setCellValue(item.getSoldeGlobalAvant());
                } else {
                    soldeAvantCell.setCellValue(0.0);
                }
                soldeAvantCell.setCellStyle(currencyStyle);
                
                // Solde Global Après
                Cell soldeApresCell = row.createCell(6);
                if (item.getSoldeGlobalApres() != null) {
                    soldeApresCell.setCellValue(item.getSoldeGlobalApres());
                } else {
                    soldeApresCell.setCellValue(0.0);
                }
                soldeApresCell.setCellStyle(currencyStyle);
                
                // Solde Partenaire Avant
                Cell soldePartAvantCell = row.createCell(7);
                if (item.getSoldePartenaireAvant() != null) {
                    soldePartAvantCell.setCellValue(item.getSoldePartenaireAvant());
                    soldePartAvantCell.setCellStyle(currencyStyle);
                } else {
                    soldePartAvantCell.setCellValue("-");
                    soldePartAvantCell.setCellStyle(defaultStyle);
                }
                
                // Solde Partenaire Après
                Cell soldePartApresCell = row.createCell(8);
                if (item.getSoldePartenaireApres() != null) {
                    soldePartApresCell.setCellValue(item.getSoldePartenaireApres());
                    soldePartApresCell.setCellStyle(currencyStyle);
                } else {
                    soldePartApresCell.setCellValue("-");
                    soldePartApresCell.setCellStyle(defaultStyle);
                }
                
                // Description
                Cell descCell = row.createCell(9);
                descCell.setCellValue(item.getDescription() != null ? item.getDescription() : "-");
                descCell.setCellStyle(defaultStyle);
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Ajouter un peu de padding
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }
            
            // Convertir en byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    
    private String getTypeLabel(String type) {
        if (type == null) return "-";
        switch (type) {
            case "FACTURE_VENTE": return "Facture Vente";
            case "FACTURE_ACHAT": return "Facture Achat";
            case "PAIEMENT_CLIENT": return "Paiement Client";
            case "PAIEMENT_FOURNISSEUR": return "Paiement Fournisseur";
            case "CHARGE_IMPOSABLE": return "Charge Imposable";
            case "CHARGE_NON_IMPOSABLE": return "Charge Non Imposable";
            case "APPORT_EXTERNE": return "Apport Externe";
            default: return type;
        }
    }

    // ========== EXPORTS COMPTABLES ==========

    /**
     * Export du journal comptable (toutes les écritures)
     */
    public byte[] exportJournalComptable(List<com.bf4invest.model.EcritureComptable> ecritures) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Journal Comptable");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Date", "Journal", "Pièce", "Compte", "Libellé", "Débit", "Crédit"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            for (com.bf4invest.model.EcritureComptable ecriture : ecritures) {
                if (ecriture.getLignes() != null) {
                    for (com.bf4invest.model.LigneEcriture ligne : ecriture.getLignes()) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(ecriture.getDateEcriture().format(DATE_FORMATTER));
                        row.createCell(1).setCellValue(ecriture.getJournal());
                        row.createCell(2).setCellValue(ecriture.getNumeroPiece());
                        row.createCell(3).setCellValue(ligne.getCompteCode());
                        row.createCell(4).setCellValue(ligne.getLibelle());
                        
                        Cell debitCell = row.createCell(5);
                        if (ligne.getDebit() != null && ligne.getDebit() > 0) {
                            debitCell.setCellValue(ligne.getDebit());
                            debitCell.setCellStyle(currencyStyle);
                        }
                        
                        Cell creditCell = row.createCell(6);
                        if (ligne.getCredit() != null && ligne.getCredit() > 0) {
                            creditCell.setCellValue(ligne.getCredit());
                            creditCell.setCellStyle(currencyStyle);
                        }
                    }
                }
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export de la balance
     */
    public byte[] exportBalance(List<com.bf4invest.model.CompteComptable> comptes) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Balance");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Code", "Libellé", "Classe", "Débit", "Crédit", "Solde"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            for (com.bf4invest.model.CompteComptable compte : comptes) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(compte.getCode());
                row.createCell(1).setCellValue(compte.getLibelle());
                row.createCell(2).setCellValue(compte.getClasse());
                
                Cell debitCell = row.createCell(3);
                debitCell.setCellValue(compte.getSoldeDebit() != null ? compte.getSoldeDebit() : 0.0);
                debitCell.setCellStyle(currencyStyle);
                
                Cell creditCell = row.createCell(4);
                creditCell.setCellValue(compte.getSoldeCredit() != null ? compte.getSoldeCredit() : 0.0);
                creditCell.setCellStyle(currencyStyle);
                
                Cell soldeCell = row.createCell(5);
                soldeCell.setCellValue(compte.getSolde() != null ? compte.getSolde() : 0.0);
                soldeCell.setCellStyle(currencyStyle);
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export du grand livre
     */
    public byte[] exportGrandLivre(List<com.bf4invest.model.EcritureComptable> ecritures, String compteCode) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Grand Livre - " + compteCode);
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Date", "Journal", "Pièce", "Libellé", "Débit", "Crédit", "Solde Progressif"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            double soldeProgressif = 0.0;
            for (com.bf4invest.model.EcritureComptable ecriture : ecritures) {
                if (ecriture.getLignes() != null) {
                    for (com.bf4invest.model.LigneEcriture ligne : ecriture.getLignes()) {
                        if (compteCode.equals(ligne.getCompteCode())) {
                            Row row = sheet.createRow(rowNum++);
                            row.createCell(0).setCellValue(ecriture.getDateEcriture().format(DATE_FORMATTER));
                            row.createCell(1).setCellValue(ecriture.getJournal());
                            row.createCell(2).setCellValue(ecriture.getNumeroPiece());
                            row.createCell(3).setCellValue(ligne.getLibelle());
                            
                            double debit = ligne.getDebit() != null ? ligne.getDebit() : 0.0;
                            double credit = ligne.getCredit() != null ? ligne.getCredit() : 0.0;
                            soldeProgressif += (debit - credit);
                            
                            Cell debitCell = row.createCell(4);
                            if (debit > 0) {
                                debitCell.setCellValue(debit);
                                debitCell.setCellStyle(currencyStyle);
                            }
                            
                            Cell creditCell = row.createCell(5);
                            if (credit > 0) {
                                creditCell.setCellValue(credit);
                                creditCell.setCellStyle(currencyStyle);
                            }
                            
                            Cell soldeCell = row.createCell(6);
                            soldeCell.setCellValue(soldeProgressif);
                            soldeCell.setCellStyle(currencyStyle);
                        }
                    }
                }
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export des déclarations TVA
     */
    public byte[] exportDeclarationsTVA(List<com.bf4invest.model.DeclarationTVA> declarations) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Déclarations TVA");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Période", "TVA Collectée 20%", "TVA Collectée 14%", "TVA Collectée 10%", "TVA Collectée 7%", 
                                "TVA Collectée Totale", "TVA Déductible 20%", "TVA Déductible 14%", "TVA Déductible 10%", 
                                "TVA Déductible 7%", "TVA Déductible Totale", "TVA à Payer", "TVA Crédit", "Statut", "Date Dépôt"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            for (com.bf4invest.model.DeclarationTVA decl : declarations) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(decl.getPeriode());
                row.createCell(1).setCellValue(decl.getTvaCollectee20() != null ? decl.getTvaCollectee20() : 0.0);
                row.createCell(2).setCellValue(decl.getTvaCollectee14() != null ? decl.getTvaCollectee14() : 0.0);
                row.createCell(3).setCellValue(decl.getTvaCollectee10() != null ? decl.getTvaCollectee10() : 0.0);
                row.createCell(4).setCellValue(decl.getTvaCollectee7() != null ? decl.getTvaCollectee7() : 0.0);
                row.createCell(5).setCellValue(decl.getTvaCollecteeTotale() != null ? decl.getTvaCollecteeTotale() : 0.0);
                row.createCell(6).setCellValue(decl.getTvaDeductible20() != null ? decl.getTvaDeductible20() : 0.0);
                row.createCell(7).setCellValue(decl.getTvaDeductible14() != null ? decl.getTvaDeductible14() : 0.0);
                row.createCell(8).setCellValue(decl.getTvaDeductible10() != null ? decl.getTvaDeductible10() : 0.0);
                row.createCell(9).setCellValue(decl.getTvaDeductible7() != null ? decl.getTvaDeductible7() : 0.0);
                row.createCell(10).setCellValue(decl.getTvaDeductibleTotale() != null ? decl.getTvaDeductibleTotale() : 0.0);
                row.createCell(11).setCellValue(decl.getTvaAPayer() != null ? decl.getTvaAPayer() : 0.0);
                row.createCell(12).setCellValue(decl.getTvaCredit() != null ? decl.getTvaCredit() : 0.0);
                row.createCell(13).setCellValue(decl.getStatut() != null ? decl.getStatut().toString() : "");
                row.createCell(14).setCellValue(decl.getDateDepot() != null ? decl.getDateDepot().format(DATE_FORMATTER) : "");
                
                for (int i = 1; i <= 12; i++) {
                    row.getCell(i).setCellStyle(currencyStyle);
                }
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Exporte l'état TVA détaillé pour un mois/année donné
     * Format exact comme dans l'image fournie
     */
    public byte[] exportEtatTVADetaille(Integer mois, Integer annee) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ETAT TVA");
            
            // Récupérer les données
            List<FactureAchat> facturesAchat = tvaService.getFacturesAchatByMonth(mois, annee);
            List<FactureVente> facturesVente = tvaService.getFacturesVenteByMonth(mois, annee);
            List<Charge> charges = tvaService.getChargesByMonth(mois, annee);
            com.bf4invest.model.CompanyInfo companyInfo = companyInfoService.getCompanyInfo();
            
            // Créer les caches pour les noms
            Map<String, Supplier> suppliersMap = supplierRepository.findAll().stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s));
            Map<String, Client> clientsMap = clientRepository.findAll().stream()
                    .collect(Collectors.toMap(Client::getId, c -> c));
            
            // Styles
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle sectionHeaderStyle = createSectionHeaderStyle(workbook);
            CellStyle columnHeaderStyle = createColumnHeaderStyle(workbook);
            CellStyle dataStyle = createDefaultStyle(workbook);
            CellStyle dataAltStyle = createDataAltStyle(workbook);
            CellStyle dataGreenStyle = createDataGreenStyle(workbook);
            CellStyle dataBlueStyle = createDataBlueStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            
            int rowNum = 0;
            
            // Titre
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            String moisAbrev = getMoisAbreviation(mois);
            titleCell.setCellValue(String.format("ETAT TVA %s/ %d", moisAbrev, annee));
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 10));
            
            // Nom entreprise
            Row companyRow = sheet.createRow(rowNum++);
            Cell companyCell = companyRow.createCell(0);
            companyCell.setCellValue(companyInfo.getRaisonSociale() != null ? companyInfo.getRaisonSociale() : "STE BF4 INVEST");
            companyCell.setCellStyle(dataStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 10));
            
            rowNum++; // Ligne vide
            
            // Section FACTURES FOURNISSEUR
            Row sectionHeaderRow = sheet.createRow(rowNum++);
            Cell sectionHeaderCell = sectionHeaderRow.createCell(0);
            sectionHeaderCell.setCellValue("FACTURES FOURNISSEUR");
            sectionHeaderCell.setCellStyle(sectionHeaderStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 10));
            
            // En-têtes colonnes
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {
                "TVA MOIS", "N° facture", "Fournisseur", "date", "MT TTC",
                "Taux TVA", "MT HT", "Somme de TVA", "Moyen payement",
                "REFERENCE", "NATURE PIECE COMPTABLE"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(columnHeaderStyle);
            }
            
            // Lignes de données FACTURES FOURNISSEUR
            double totalTTCFournisseur = 0.0;
            double totalHTFournisseur = 0.0;
            double totalTVAFournisseur = 0.0;
            
            for (int i = 0; i < facturesAchat.size(); i++) {
                FactureAchat facture = facturesAchat.get(i);
                Row dataRow = sheet.createRow(rowNum++);
                
                // Déterminer le style (alternance blanc/gris, vert pour 10%)
                CellStyle rowStyle = (i % 2 == 0) ? dataStyle : dataAltStyle;
                if (facture.getTvaRate() != null && Math.abs(facture.getTvaRate() - 0.10) < 0.001) {
                    rowStyle = dataGreenStyle;
                }
                
                // TVA MOIS
                Cell cell0 = dataRow.createCell(0);
                cell0.setCellValue(String.format("%s-%d", moisAbrev, annee % 100));
                cell0.setCellStyle(rowStyle);
                
                // N° facture
                Cell cell1 = dataRow.createCell(1);
                cell1.setCellValue(facture.getNumeroFactureAchat() != null ? facture.getNumeroFactureAchat() : "");
                cell1.setCellStyle(rowStyle);
                
                // Fournisseur
                Cell cell2 = dataRow.createCell(2);
                Supplier supplier = facture.getFournisseurId() != null ? suppliersMap.get(facture.getFournisseurId()) : null;
                cell2.setCellValue(supplier != null ? supplier.getNom() : "");
                cell2.setCellStyle(rowStyle);
                
                // date
                Cell cell3 = dataRow.createCell(3);
                if (facture.getDateFacture() != null) {
                    cell3.setCellValue(facture.getDateFacture().format(DATE_FORMATTER));
                    cell3.setCellStyle(dateStyle);
                } else {
                    cell3.setCellValue("");
                    cell3.setCellStyle(rowStyle);
                }
                
                // MT TTC
                Cell cell4 = dataRow.createCell(4);
                Double mtTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
                cell4.setCellValue(mtTTC);
                CellStyle currencyStyleWithBg = createCurrencyStyleWithBackground(workbook, rowStyle);
                cell4.setCellStyle(currencyStyleWithBg);
                totalTTCFournisseur += mtTTC;
                
                // Taux TVA
                Cell cell5 = dataRow.createCell(5);
                Double tauxTVA = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
                cell5.setCellValue(tauxTVA);
                CellStyle percentStyleWithBg = createPercentStyleWithBackground(workbook, rowStyle);
                cell5.setCellStyle(percentStyleWithBg);
                
                // MT HT
                Cell cell6 = dataRow.createCell(6);
                Double mtHT = facture.getTotalHT() != null ? facture.getTotalHT() : 0.0;
                cell6.setCellValue(mtHT);
                cell6.setCellStyle(currencyStyleWithBg);
                totalHTFournisseur += mtHT;
                
                // Somme de TVA
                Cell cell7 = dataRow.createCell(7);
                Double sommeTVA = facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0;
                cell7.setCellValue(sommeTVA);
                cell7.setCellStyle(currencyStyleWithBg);
                totalTVAFournisseur += sommeTVA;
                
                // Moyen payement
                Cell cell8 = dataRow.createCell(8);
                cell8.setCellValue(facture.getModePaiement() != null ? facture.getModePaiement() : "");
                cell8.setCellStyle(rowStyle);
                
                // REFERENCE
                Cell cell9 = dataRow.createCell(9);
                // Chercher la référence dans les paiements
                String reference = "";
                if (facture.getPaiements() != null && !facture.getPaiements().isEmpty()) {
                    reference = facture.getPaiements().stream()
                            .filter(p -> p.getReference() != null && !p.getReference().isEmpty())
                            .map(Paiement::getReference)
                            .findFirst()
                            .orElse("");
                }
                cell9.setCellValue(reference);
                cell9.setCellStyle(rowStyle);
                
                // NATURE PIECE COMPTABLE
                Cell cell10 = dataRow.createCell(10);
                cell10.setCellValue("COPIE/ORIGINALE A RECEVOIR");
                cell10.setCellStyle(rowStyle);
            }
            
            // Ajouter les charges dans la section FACTURES FOURNISSEUR (avec fond bleu ciel)
            int factureCount = facturesAchat.size();
            for (Charge charge : charges) {
                Row dataRow = sheet.createRow(rowNum++);
                CellStyle rowStyle = dataBlueStyle; // Fond bleu ciel pour les charges
                
                // TVA MOIS
                Cell cell0 = dataRow.createCell(0);
                cell0.setCellValue(String.format("%s.-%d", moisAbrev, annee % 100));
                cell0.setCellStyle(rowStyle);
                
                // N° facture (libellé de la charge)
                Cell cell1 = dataRow.createCell(1);
                cell1.setCellValue(charge.getLibelle() != null ? charge.getLibelle() : "");
                cell1.setCellStyle(rowStyle);
                
                // Fournisseur (catégorie de la charge)
                Cell cell2 = dataRow.createCell(2);
                cell2.setCellValue(charge.getCategorie() != null ? charge.getCategorie() : "");
                cell2.setCellStyle(rowStyle);
                
                // date (datePaiement si payée, sinon dateEcheance)
                Cell cell3 = dataRow.createCell(3);
                LocalDate chargeDate = charge.getDatePaiement() != null ? charge.getDatePaiement() : charge.getDateEcheance();
                if (chargeDate != null) {
                    cell3.setCellValue(chargeDate.format(DATE_FORMATTER));
                    CellStyle dateStyleWithBg = createDateStyleWithBackground(workbook, rowStyle);
                    cell3.setCellStyle(dateStyleWithBg);
                } else {
                    cell3.setCellValue("");
                    cell3.setCellStyle(rowStyle);
                }
                
                // MT TTC (montant de la charge avec TVA 20% par défaut)
                Cell cell4 = dataRow.createCell(4);
                Double chargeMontant = charge.getMontant() != null ? charge.getMontant() : 0.0;
                // Calculer TTC avec TVA 20%
                Double chargeTTC = chargeMontant * 1.20;
                cell4.setCellValue(chargeTTC);
                CellStyle currencyStyleWithBgCharge = createCurrencyStyleWithBackground(workbook, rowStyle);
                cell4.setCellStyle(currencyStyleWithBgCharge);
                totalTTCFournisseur += chargeTTC;
                
                // Taux TVA (20% par défaut pour les charges)
                Cell cell5 = dataRow.createCell(5);
                cell5.setCellValue(0.20);
                CellStyle percentStyleWithBgCharge = createPercentStyleWithBackground(workbook, rowStyle);
                cell5.setCellStyle(percentStyleWithBgCharge);
                
                // MT HT
                Cell cell6 = dataRow.createCell(6);
                cell6.setCellValue(chargeMontant);
                cell6.setCellStyle(currencyStyleWithBgCharge);
                totalHTFournisseur += chargeMontant;
                
                // Somme de TVA
                Cell cell7 = dataRow.createCell(7);
                Double chargeTVA = chargeTTC - chargeMontant;
                cell7.setCellValue(chargeTVA);
                cell7.setCellStyle(currencyStyleWithBgCharge);
                totalTVAFournisseur += chargeTVA;
                
                // Moyen payement
                Cell cell8 = dataRow.createCell(8);
                cell8.setCellValue("");
                cell8.setCellStyle(rowStyle);
                
                // REFERENCE
                Cell cell9 = dataRow.createCell(9);
                cell9.setCellValue("");
                cell9.setCellStyle(rowStyle);
                
                // NATURE PIECE COMPTABLE
                Cell cell10 = dataRow.createCell(10);
                cell10.setCellValue("COPIE/ORIGINALE A RECEVOIR");
                cell10.setCellStyle(rowStyle);
            }
            
            // Ligne de total FACTURES FOURNISSEUR
            Row totalRowFournisseur = sheet.createRow(rowNum++);
            Cell totalLabel = totalRowFournisseur.createCell(0);
            totalLabel.setCellValue("Total MT TTC");
            totalLabel.setCellStyle(totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
            
            Cell totalTTC = totalRowFournisseur.createCell(4);
            totalTTC.setCellValue(totalTTCFournisseur);
            CellStyle totalCurrencyStyle = createTotalCurrencyStyle(workbook);
            totalTTC.setCellStyle(totalCurrencyStyle);
            
            Cell totalHTLabel = totalRowFournisseur.createCell(5);
            totalHTLabel.setCellValue("Total MT HT");
            totalHTLabel.setCellStyle(totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 5, 6));
            
            Cell totalHT = totalRowFournisseur.createCell(7);
            totalHT.setCellValue(totalHTFournisseur);
            totalHT.setCellStyle(totalCurrencyStyle);
            
            Cell totalTVALabel = totalRowFournisseur.createCell(8);
            totalTVALabel.setCellValue("Total Somme de TVA");
            totalTVALabel.setCellStyle(totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 8, 9));
            
            Cell totalTVA = totalRowFournisseur.createCell(10);
            totalTVA.setCellValue(totalTVAFournisseur);
            totalTVA.setCellStyle(totalCurrencyStyle);
            
            rowNum++; // Ligne vide
            
            // Section FACTURES CLIENTS
            Row sectionClientRow = sheet.createRow(rowNum++);
            Cell sectionClientCell = sectionClientRow.createCell(0);
            sectionClientCell.setCellValue("FACTURES CLIENTS");
            sectionClientCell.setCellStyle(sectionHeaderStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 10));
            
            // En-têtes colonnes (même structure)
            Row headerClientRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerClientRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(columnHeaderStyle);
            }
            
            // Lignes de données FACTURES CLIENTS
            double totalTTCClient = 0.0;
            double totalHTClient = 0.0;
            double totalTVAClient = 0.0;
            
            for (int i = 0; i < facturesVente.size(); i++) {
                FactureVente facture = facturesVente.get(i);
                Row dataRow = sheet.createRow(rowNum++);
                
                // Déterminer le style
                CellStyle rowStyle = (i % 2 == 0) ? dataStyle : dataAltStyle;
                if (facture.getTvaRate() != null && Math.abs(facture.getTvaRate() - 0.10) < 0.001) {
                    rowStyle = dataGreenStyle;
                }
                
                // TVA MOIS (format: "nov.-25" avec point avant le tiret)
                Cell cell0 = dataRow.createCell(0);
                cell0.setCellValue(String.format("%s.-%d", moisAbrev, annee % 100));
                cell0.setCellStyle(rowStyle);
                
                // N° facture
                Cell cell1 = dataRow.createCell(1);
                cell1.setCellValue(facture.getNumeroFactureVente() != null ? facture.getNumeroFactureVente() : "");
                cell1.setCellStyle(rowStyle);
                
                // Client
                Cell cell2 = dataRow.createCell(2);
                Client client = facture.getClientId() != null ? clientsMap.get(facture.getClientId()) : null;
                cell2.setCellValue(client != null ? client.getNom() : "");
                cell2.setCellStyle(rowStyle);
                
                // date
                Cell cell3 = dataRow.createCell(3);
                if (facture.getDateFacture() != null) {
                    cell3.setCellValue(facture.getDateFacture().format(DATE_FORMATTER));
                    cell3.setCellStyle(dateStyle);
                } else {
                    cell3.setCellValue("");
                    cell3.setCellStyle(rowStyle);
                }
                
                // MT TTC
                Cell cell4 = dataRow.createCell(4);
                Double mtTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
                cell4.setCellValue(mtTTC);
                CellStyle currencyStyleWithBgClient = createCurrencyStyleWithBackground(workbook, rowStyle);
                cell4.setCellStyle(currencyStyleWithBgClient);
                totalTTCClient += mtTTC;
                
                // Taux TVA
                Cell cell5 = dataRow.createCell(5);
                Double tauxTVA = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
                cell5.setCellValue(tauxTVA);
                CellStyle percentStyleWithBgClient = createPercentStyleWithBackground(workbook, rowStyle);
                cell5.setCellStyle(percentStyleWithBgClient);
                
                // MT HT
                Cell cell6 = dataRow.createCell(6);
                Double mtHT = facture.getTotalHT() != null ? facture.getTotalHT() : 0.0;
                cell6.setCellValue(mtHT);
                cell6.setCellStyle(currencyStyleWithBgClient);
                totalHTClient += mtHT;
                
                // Somme de TVA
                Cell cell7 = dataRow.createCell(7);
                Double sommeTVA = facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0;
                cell7.setCellValue(sommeTVA);
                cell7.setCellStyle(currencyStyleWithBgClient);
                totalTVAClient += sommeTVA;
                
                // Moyen payement
                Cell cell8 = dataRow.createCell(8);
                cell8.setCellValue(facture.getModePaiement() != null ? facture.getModePaiement() : "");
                cell8.setCellStyle(rowStyle);
                
                // REFERENCE
                Cell cell9 = dataRow.createCell(9);
                String reference = "";
                if (facture.getPaiements() != null && !facture.getPaiements().isEmpty()) {
                    reference = facture.getPaiements().stream()
                            .filter(p -> p.getReference() != null && !p.getReference().isEmpty())
                            .map(Paiement::getReference)
                            .findFirst()
                            .orElse("");
                }
                cell9.setCellValue(reference);
                cell9.setCellStyle(rowStyle);
                
                // NATURE PIECE COMPTABLE
                Cell cell10 = dataRow.createCell(10);
                cell10.setCellValue("COPIE/ORIGINALE A RECEVOIR");
                cell10.setCellStyle(rowStyle);
            }
            
            // Si aucune facture client, ajouter une ligne vide avec 0,00
            if (facturesVente.isEmpty()) {
                Row emptyRow = sheet.createRow(rowNum++);
                Cell emptyCell0 = emptyRow.createCell(0);
                emptyCell0.setCellValue(String.format("%s.-%d", moisAbrev, annee % 100));
                emptyCell0.setCellStyle(dataStyle);
                
                for (int i = 1; i < 11; i++) {
                    Cell emptyCell = emptyRow.createCell(i);
                    if (i == 4 || i == 6 || i == 7) {
                        emptyCell.setCellValue(0.0);
                        emptyCell.setCellStyle(currencyStyle);
                    } else {
                        emptyCell.setCellValue("");
                        emptyCell.setCellStyle(dataStyle);
                    }
                }
            }
            
            // Ligne de total FACTURES CLIENTS
            Row totalRowClient = sheet.createRow(rowNum++);
            Cell totalLabelClient = totalRowClient.createCell(0);
            totalLabelClient.setCellValue("Total MT TTC");
            totalLabelClient.setCellStyle(totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
            
            Cell totalTTCClientCell = totalRowClient.createCell(4);
            totalTTCClientCell.setCellValue(totalTTCClient);
            CellStyle totalCurrencyStyleClient = createTotalCurrencyStyle(workbook);
            totalTTCClientCell.setCellStyle(totalCurrencyStyleClient);
            
            Cell totalHTLabelClient = totalRowClient.createCell(5);
            totalHTLabelClient.setCellValue("Total MT HT");
            totalHTLabelClient.setCellStyle(totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 5, 6));
            
            Cell totalHTClientCell = totalRowClient.createCell(7);
            totalHTClientCell.setCellValue(totalHTClient);
            totalHTClientCell.setCellStyle(totalCurrencyStyleClient);
            
            Cell totalTVALabelClient = totalRowClient.createCell(8);
            totalTVALabelClient.setCellValue("Total Somme de TVA");
            totalTVALabelClient.setCellStyle(totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 8, 9));
            
            Cell totalTVAClientCell = totalRowClient.createCell(10);
            totalTVAClientCell.setCellValue(totalTVAClient);
            totalTVAClientCell.setCellStyle(totalCurrencyStyleClient);
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < 11; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1000, 3000));
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
    
    /**
     * Exporte les BCs au format import Excel (une ligne par produit × client)
     */
    public byte[] exportBCsToImportFormat(List<BandeCommande> bcs) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export Import Format");
            
            // Créer un cache des clients et fournisseurs
            Map<String, Client> clientsMap = clientRepository.findAll().stream()
                    .collect(Collectors.toMap(Client::getId, c -> c));
            Map<String, Supplier> suppliersMap = supplierRepository.findAll().stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s));
            
            // Styles
            CellStyle defaultStyle = createDefaultStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle frenchNumberStyle = createFrenchNumberStyle(workbook);
            CellStyle lightGreenRowStyle = createLightGreenRowStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            
            // Créer l'en-tête avec les colonnes dans l'ordre exact du format image
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "N° BC",
                "FRS",
                "N FAC FRS",
                "N° FAC VTE",
                "DATE FAC VTE",
                "ICE",
                "CLENT",
                "N° ARTICLE",
                "DESIGNATION",
                "U",
                "QT ACHAT",
                "PRIX ACHAT U HT",
                "PRIX ACHAT U TTC",
                "PRIX ACHAT T HT",
                "TX TVA",
                "FACTURE ACHAT TTC",
                "QT LIVREE CLT",
                "PRIX DE VENTE U HT",
                "FACTURE VENTE T TTC"
            };
            
            // Couleurs spécifiques pour chaque colonne (dans l'ordre des colonnes)
            String[] headerColors = {
                "#DAE9F8",  // 1. N° BC
                "#FFFFCC",  // 2. FRS
                "#FFFFCC",  // 3. N FAC FRS
                "#FFEFFF",  // 4. N° FAC VTE
                "#FFEFFF",  // 5. DATE FAC VTE
                "#FFEFFF",  // 6. ICE
                "#FFEFFF",  // 7. CLENT
                "#DAF2D0",  // 8. N° ARTICLE
                "#DAF2D0",  // 9. DESIGNATION
                "#DAF2D0",  // 10. U
                "#DAF2D0",  // 11. QT ACHAT
                "#FFFFCC",  // 12. PRIX ACHAT U HT
                "#FFFFCC",  // 13. PRIX ACHAT U TTC
                "#FFFFCC",  // 14. PRIX ACHAT T HT
                "#FFFFCC",  // 15. TX TVA
                "#FFFFCC",  // 16. FACTURE ACHAT TTC
                "#FFEFFF",  // 17. QT LIVREE CLT
                "#FFEFFF",  // 18. PRIX DE VENTE U HT
                "#FFEFFF"   // 19. FACTURE VENTE T TTC
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle headerStyle = createHeaderStyleWithColor(workbook, headerColors[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Générer les lignes d'export
            List<ImportFormatRow> exportRows = new ArrayList<>();
            
            for (BandeCommande bc : bcs) {
                // Récupérer la facture achat liée
                FactureAchat factureAchat = null;
                if (bc.getNumeroBC() != null) {
                    List<FactureAchat> facturesAchat = factureAchatRepository.findByBcReference(bc.getNumeroBC());
                    if (!facturesAchat.isEmpty()) {
                        factureAchat = facturesAchat.get(0); // Une facture achat par BC
                    }
                }
                
                // Récupérer les factures vente liées
                List<FactureVente> facturesVente = new ArrayList<>();
                if (bc.getId() != null) {
                    facturesVente.addAll(factureVenteRepository.findByBandeCommandeId(bc.getId()));
                }
                if (bc.getNumeroBC() != null) {
                    List<FactureVente> fvByRef = factureVenteRepository.findByBcReference(bc.getNumeroBC());
                    for (FactureVente fv : fvByRef) {
                        if (facturesVente.stream().noneMatch(existing -> existing.getId().equals(fv.getId()))) {
                            facturesVente.add(fv);
                        }
                    }
                }
                
                // Créer une map des factures vente par clientId pour faciliter l'accès
                Map<String, FactureVente> factureVenteByClientId = facturesVente.stream()
                        .filter(fv -> fv.getClientId() != null)
                        .collect(Collectors.toMap(FactureVente::getClientId, fv -> fv, (fv1, fv2) -> fv1));
                
                // Créer une map des lignes vente par produitKey × clientId
                Map<String, Map<String, LigneVente>> lignesVenteByProduitAndClient = new HashMap<>();
                if (bc.getClientsVente() != null) {
                    for (ClientVente cv : bc.getClientsVente()) {
                        if (cv.getLignesVente() != null) {
                            for (LigneVente lv : cv.getLignesVente()) {
                                String produitKey = createProduitKey(lv.getProduitRef(), lv.getDesignation(), lv.getUnite());
                                lignesVenteByProduitAndClient
                                    .computeIfAbsent(produitKey, k -> new HashMap<>())
                                    .put(cv.getClientId(), lv);
                            }
                        }
                    }
                }
                
                // Pour chaque ligne achat, créer des lignes d'export
                if (bc.getLignesAchat() != null) {
                    for (LigneAchat la : bc.getLignesAchat()) {
                        String produitKey = createProduitKey(la.getProduitRef(), la.getDesignation(), la.getUnite());
                        Map<String, LigneVente> lignesVenteForProduit = lignesVenteByProduitAndClient.getOrDefault(produitKey, new HashMap<>());
                        
                        // Si ce produit est vendu à des clients, créer une ligne par client
                        if (!lignesVenteForProduit.isEmpty()) {
                            for (Map.Entry<String, LigneVente> entry : lignesVenteForProduit.entrySet()) {
                                String clientId = entry.getKey();
                                LigneVente lv = entry.getValue();
                                FactureVente fv = factureVenteByClientId.get(clientId);
                                
                                ImportFormatRow row = createImportFormatRow(bc, la, factureAchat, lv, fv, 
                                        clientsMap.get(clientId), suppliersMap.get(bc.getFournisseurId()));
                                exportRows.add(row);
                            }
                        } else {
                            // Si ce produit n'est pas vendu, créer une ligne sans info vente
                            ImportFormatRow row = createImportFormatRow(bc, la, factureAchat, null, null, 
                                    null, suppliersMap.get(bc.getFournisseurId()));
                            exportRows.add(row);
                        }
                    }
                }
            }
            
            // Créer les styles pour les lignes alternées
            CellStyle whiteRowTextStyle = defaultStyle;
            CellStyle whiteRowNumberStyle = frenchNumberStyle;
            CellStyle whiteRowDateStyle = dateStyle;
            CellStyle whiteRowPercentStyle = percentStyle;
            
            CellStyle lightGreenRowTextStyle = createLightGreenRowStyle(workbook);
            CellStyle lightGreenRowNumberStyle = createFrenchNumberStyleWithBackground(workbook, lightGreenRowTextStyle);
            CellStyle lightGreenRowDateStyle = createDateStyleWithBackgroundForExport(workbook, lightGreenRowTextStyle);
            CellStyle lightGreenRowPercentStyle = createPercentStyleWithBackground(workbook, lightGreenRowTextStyle);
            
            // Remplir les données dans Excel avec styles alternés
            int dataRowNum = 1;
            for (ImportFormatRow row : exportRows) {
                Row excelRow = sheet.createRow(dataRowNum++);
                int colIndex = 0;
                
                // Déterminer le style de ligne (alternance blanc/vert clair)
                // rowNum % 2 == 0 -> blanc (default), rowNum % 2 == 1 -> vert clair
                boolean isLightGreenRow = (dataRowNum - 1) % 2 == 1;
                CellStyle rowTextStyle = isLightGreenRow ? lightGreenRowTextStyle : whiteRowTextStyle;
                CellStyle rowNumberStyle = isLightGreenRow ? lightGreenRowNumberStyle : whiteRowNumberStyle;
                CellStyle rowDateStyle = isLightGreenRow ? lightGreenRowDateStyle : whiteRowDateStyle;
                CellStyle rowPercentStyle = isLightGreenRow ? lightGreenRowPercentStyle : whiteRowPercentStyle;
                
                // N° BC
                setCellValue(excelRow, colIndex++, row.numeroBC, rowTextStyle);
                
                // FRS (après N° BC, avant N° FAC FRS)
                setCellValue(excelRow, colIndex++, row.fournisseur, rowTextStyle);
                
                // N FAC FRS
                setCellValue(excelRow, colIndex++, row.numeroFactureFournisseur, rowTextStyle);
                
                // N° FAC VTE
                setCellValue(excelRow, colIndex++, row.numeroFactureVente, rowTextStyle);
                
                // DATE FAC VTE
                Cell dateFVCell = excelRow.createCell(colIndex++);
                if (row.dateFactureVente != null) {
                    dateFVCell.setCellValue(row.dateFactureVente.format(DATE_FORMATTER));
                    dateFVCell.setCellStyle(rowDateStyle);
                } else {
                    dateFVCell.setCellValue("");
                    dateFVCell.setCellStyle(rowTextStyle);
                }
                
                // ICE
                setCellValue(excelRow, colIndex++, row.ice, rowTextStyle);
                
                // CLENT
                setCellValue(excelRow, colIndex++, row.client, rowTextStyle);
                
                // N° ARTICLE
                setCellValue(excelRow, colIndex++, row.numeroArticle, rowTextStyle);
                
                // DESIGNATION
                setCellValue(excelRow, colIndex++, row.designation, rowTextStyle);
                
                // U
                setCellValue(excelRow, colIndex++, row.unite, rowTextStyle);
                
                // QT ACHAT (au lieu de QT BC)
                setCellValue(excelRow, colIndex++, row.quantiteBC, rowNumberStyle);
                
                // PRIX ACHAT U HT (au lieu de PU ACHAT HT)
                setCellValue(excelRow, colIndex++, row.prixAchatUnitaireHT, rowNumberStyle);
                
                // PRIX ACHAT U TTC (nouvelle colonne)
                setCellValue(excelRow, colIndex++, row.prixAchatUnitaireTTC, rowNumberStyle);
                
                // PRIX ACHAT T HT
                setCellValue(excelRow, colIndex++, row.prixAchatTotalHT, rowNumberStyle);
                
                // TX TVA
                Cell tvaCell = excelRow.createCell(colIndex++);
                if (row.tauxTVA != null) {
                    tvaCell.setCellValue(row.tauxTVA / 100.0); // Format Excel pourcentage
                    tvaCell.setCellStyle(rowPercentStyle);
                } else {
                    tvaCell.setCellValue("");
                    tvaCell.setCellStyle(rowTextStyle);
                }
                
                // FACTURE ACHAT TTC
                setCellValue(excelRow, colIndex++, row.factureAchatTTC, rowNumberStyle);
                
                // QT LIVREE CLT (au lieu de QT LIVREE)
                setCellValue(excelRow, colIndex++, row.quantiteLivree, rowNumberStyle);
                
                // PRIX DE VENTE U HT (au lieu de PU VENTE HT)
                setCellValue(excelRow, colIndex++, row.prixVenteUnitaireHT, rowNumberStyle);
                
                // FACTURE VENTE T TTC (au lieu de FACTURE VENTE TTC, sans PU VENTE TTC ni MARGE U TTC)
                setCellValue(excelRow, colIndex++, row.factureVenteTTC, rowNumberStyle);
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1000, 3000));
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
    
    /**
     * Crée une clé produit unique (utilisée pour matcher lignes achat et vente)
     */
    private String createProduitKey(String produitRef, String designation, String unite) {
        return (produitRef != null ? produitRef : "") + "|" + 
               (designation != null ? designation : "") + "|" + 
               (unite != null ? unite : "U");
    }
    
    /**
     * Crée une ligne d'export au format import
     */
    private ImportFormatRow createImportFormatRow(BandeCommande bc, LigneAchat la, 
            FactureAchat fa, LigneVente lv, FactureVente fv, Client client, Supplier supplier) {
        ImportFormatRow row = new ImportFormatRow();
        
        // Informations BC
        row.numeroBC = bc.getNumeroBC();
        row.dateBC = bc.getDateBC();
        
        // Informations facture achat
        if (fa != null) {
            row.numeroFactureFournisseur = fa.getNumeroFactureFournisseur();
            row.dateFactureAchat = fa.getDateFacture();
        }
        
        // Informations facture vente
        if (fv != null) {
            row.numeroFactureVente = fv.getNumeroFactureVente();
            row.dateFactureVente = fv.getDateFacture();
        }
        
        // ICE (du client si présent, sinon du fournisseur)
        if (client != null && client.getIce() != null) {
            row.ice = client.getIce();
        } else if (supplier != null && supplier.getIce() != null) {
            row.ice = supplier.getIce();
        }
        
        // Fournisseur et client
        row.fournisseur = supplier != null ? supplier.getNom() : null;
        row.client = client != null ? client.getNom() : null;
        
        // Informations produit (depuis ligne achat)
        row.numeroArticle = la.getProduitRef();
        row.designation = la.getDesignation();
        row.unite = la.getUnite() != null ? la.getUnite() : "U";
        row.quantiteBC = la.getQuantiteAchetee();
        row.prixAchatUnitaireHT = la.getPrixAchatUnitaireHT();
        row.prixAchatTotalHT = la.getTotalHT();
        row.tauxTVA = la.getTva();
        
        // Calculer prix achat unitaire TTC et total TTC
        if (row.prixAchatUnitaireHT != null && row.tauxTVA != null) {
            row.prixAchatUnitaireTTC = row.prixAchatUnitaireHT * (1 + row.tauxTVA / 100.0);
            if (row.quantiteBC != null) {
                row.prixAchatTTC = row.prixAchatUnitaireTTC * row.quantiteBC;
            }
        }
        // FACTURE ACHAT TTC : utiliser le total TTC de la ligne
        row.factureAchatTTC = la.getTotalTTC();
        
        // Informations vente (si présente)
        if (lv != null) {
            row.quantiteLivree = lv.getQuantiteVendue();
            row.prixVenteUnitaireHT = lv.getPrixVenteUnitaireHT();
            
            // Calculer prix vente unitaire TTC
            if (row.prixVenteUnitaireHT != null && lv.getTva() != null) {
                row.prixVenteUnitaireTTC = row.prixVenteUnitaireHT * (1 + lv.getTva() / 100.0);
            }
            
            // FACTURE VENTE TTC : utiliser le total TTC de la ligne
            row.factureVenteTTC = lv.getTotalTTC();
            
            // MARGE U TTC
            if (row.prixVenteUnitaireTTC != null && row.prixAchatUnitaireTTC != null) {
                row.margeUnitaireTTC = row.prixVenteUnitaireTTC - row.prixAchatUnitaireTTC;
            }
        }
        
        return row;
    }
    
    /**
     * Retourne l'abréviation du mois en français
     */
    private String getMoisAbreviation(Integer mois) {
        String[] moisAbrev = {"janv.", "févr.", "mars", "avr.", "mai", "juin",
                "juil.", "août", "sept.", "oct.", "nov.", "déc."};
        if (mois >= 1 && mois <= 12) {
            return moisAbrev[mois - 1];
        }
        return "janv.";
    }
    
    /**
     * Style pour le titre
     */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }
    
    /**
     * Style pour les en-têtes de section (orange/brun)
     */
    private CellStyle createSectionHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Style pour les en-têtes de colonnes (orange/brun clair)
     */
    private CellStyle createColumnHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        // Couleur orange/brun clair
        style.setFillForegroundColor(IndexedColors.TAN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Style pour les lignes alternées (gris clair)
     */
    private CellStyle createDataAltStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Style pour les lignes avec TVA 10% (vert clair)
     */
    private CellStyle createDataGreenStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Vert clair
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Style pour les charges (bleu ciel)
     */
    private CellStyle createDataBlueStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Bleu ciel (light blue)
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Style pour la ligne de total (jaune)
     */
    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        // Jaune
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }
    
    /**
     * Style currency avec fond (pour les lignes de données)
     */
    private CellStyle createCurrencyStyleWithBackground(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        DataFormat format = workbook.createDataFormat();
        // Format avec virgule pour décimales (Excel utilisera la locale du système pour les séparateurs)
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    /**
     * Style percent avec fond (pour les lignes de données)
     */
    private CellStyle createPercentStyleWithBackground(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    /**
     * Style date avec fond (pour les lignes de données)
     */
    private CellStyle createDateStyleWithBackground(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
    
    /**
     * Style currency pour les totaux (jaune avec format monétaire)
     */
    private CellStyle createTotalCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        // Format standard avec virgule pour décimales
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }
    
    /**
     * Style pour le header jaune/doré avec texte gras noir
     */
    private CellStyle createYellowHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Crée un style de header avec une couleur hex personnalisée
     * @param workbook Le workbook XSSFWorkbook
     * @param hexColor Couleur hex (ex: "#DAE9F8")
     * @return CellStyle avec la couleur spécifiée
     */
    private CellStyle createHeaderStyleWithColor(Workbook workbook, String hexColor) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
        // Convertir hex en RGB pour XSSFColor
        if (workbook instanceof XSSFWorkbook && hexColor != null && hexColor.startsWith("#")) {
            try {
                // Enlever le # et convertir en RGB
                String hex = hexColor.substring(1);
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                
                XSSFColor color = new XSSFColor(new byte[]{(byte)r, (byte)g, (byte)b}, null);
                XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
                xssfStyle.setFillForegroundColor(color);
            } catch (Exception e) {
                // En cas d'erreur, utiliser la couleur par défaut
                style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            }
        } else {
            style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        }
        
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Style pour les lignes vert clair (alternance)
     */
    private CellStyle createLightGreenRowStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Style pour les nombres avec format français (virgule/espace)
     * Format: # ##0,00 (espace pour milliers, virgule pour décimales)
     */
    private CellStyle createFrenchNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        // Format Excel pour français: espace pour milliers, virgule pour décimales
        // Note: Excel utilisera la locale du système, mais on peut forcer avec # ##0,00
        style.setDataFormat(format.getFormat("#,##0.00")); // Excel convertira selon locale
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Style pour les nombres français avec fond (pour alternance de lignes)
     */
    private CellStyle createFrenchNumberStyleWithBackground(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    /**
     * Style pour les dates avec fond (pour alternance de lignes)
     */
    private CellStyle createDateStyleWithBackgroundForExport(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}

