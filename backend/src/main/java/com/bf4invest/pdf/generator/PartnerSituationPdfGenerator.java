package com.bf4invest.pdf.generator;

import com.bf4invest.dto.PartnerSituationResponse;
import com.bf4invest.pdf.helper.PdfDocumentHelper;
import com.bf4invest.pdf.helper.PdfLogoHelper;
import com.bf4invest.service.CompanyInfoService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerSituationPdfGenerator {
    
    private final CompanyInfoService companyInfoService;
    
    private static final Color BLUE_DARK = new Color(30, 64, 124);
    private static final Color BLUE_LIGHT = new Color(200, 220, 240);
    private static final Color GRAY_LIGHT = new Color(245, 245, 245);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    
    static {
        NUMBER_FORMAT.setMinimumFractionDigits(2);
        NUMBER_FORMAT.setMaximumFractionDigits(2);
        NUMBER_FORMAT.setGroupingUsed(true);
    }
    
    public byte[] generate(PartnerSituationResponse situation) throws DocumentException, IOException {
        Document document = PdfDocumentHelper.createA4Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        try {
            addHeader(document, writer);
            addPartnerInfo(document, situation);
            addSummarySection(document, situation);
            addFacturesTable(document, situation);
            addPrevisionsTable(document, situation);
            addFooter(document);
        } finally {
            document.close();
        }
        
        return baos.toByteArray();
    }
    
    private void addHeader(Document document, PdfWriter writer) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 8f});
        headerTable.setSpacingAfter(20);
        
        // Logo
        PdfPCell logoCell = PdfLogoHelper.createLogoCell(writer, 100f, 75f);
        headerTable.addCell(logoCell);
        
        // Informations société
        PdfPCell infoCell = new PdfPCell();
        infoCell.setBorder(PdfPCell.NO_BORDER);
        infoCell.setPaddingLeft(10);
        
        com.bf4invest.model.CompanyInfo companyInfo = companyInfoService.getCompanyInfo();
        String raisonSociale = companyInfo.getRaisonSociale() != null ? companyInfo.getRaisonSociale() : "STE BF4 INVEST";
        
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK);
        Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        
        Paragraph headerPara = new Paragraph();
        headerPara.add(new Chunk(raisonSociale + "\n", titleFont));
        headerPara.add(new Chunk("SITUATION FINANCIÈRE AVEC PRÉVISIONS\n\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK)));
        headerPara.add(new Chunk("Date de génération: " + LocalDate.now().format(DATE_FORMATTER), infoFont));
        
        infoCell.addElement(headerPara);
        headerTable.addCell(infoCell);
        
        document.add(headerTable);
    }
    
    private void addPartnerInfo(Document document, PartnerSituationResponse situation) throws DocumentException {
        PartnerSituationResponse.PartnerInfo partner = situation.getPartnerInfo();
        
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        
        Paragraph partnerPara = new Paragraph();
        partnerPara.add(new Chunk("Informations du " + (partner.getType().equals("CLIENT") ? "Client" : "Fournisseur") + "\n", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK)));
        partnerPara.add(new Chunk("Nom: ", labelFont));
        partnerPara.add(new Chunk(partner.getNom() + "\n", valueFont));
        
        if (partner.getIce() != null) {
            partnerPara.add(new Chunk("ICE: ", labelFont));
            partnerPara.add(new Chunk(partner.getIce() + "\n", valueFont));
        }
        
        if (partner.getReference() != null) {
            partnerPara.add(new Chunk("Référence: ", labelFont));
            partnerPara.add(new Chunk(partner.getReference() + "\n", valueFont));
        }
        
        if (partner.getAdresse() != null) {
            partnerPara.add(new Chunk("Adresse: ", labelFont));
            partnerPara.add(new Chunk(partner.getAdresse() + "\n", valueFont));
        }
        
        if (partner.getTelephone() != null) {
            partnerPara.add(new Chunk("Téléphone: ", labelFont));
            partnerPara.add(new Chunk(partner.getTelephone() + "\n", valueFont));
        }
        
        if (partner.getEmail() != null) {
            partnerPara.add(new Chunk("Email: ", labelFont));
            partnerPara.add(new Chunk(partner.getEmail() + "\n", valueFont));
        }
        
        if (partner.getRib() != null) {
            partnerPara.add(new Chunk("RIB: ", labelFont));
            partnerPara.add(new Chunk(partner.getRib() + "\n", valueFont));
        }
        
        if (partner.getBanque() != null) {
            partnerPara.add(new Chunk("Banque: ", labelFont));
            partnerPara.add(new Chunk(partner.getBanque() + "\n", valueFont));
        }
        
        if (situation.getDateFrom() != null || situation.getDateTo() != null) {
            partnerPara.add(new Chunk("\nPériode: ", labelFont));
            String periode = "";
            if (situation.getDateFrom() != null) {
                periode += "Du " + situation.getDateFrom().format(DATE_FORMATTER);
            }
            if (situation.getDateTo() != null) {
                periode += " au " + situation.getDateTo().format(DATE_FORMATTER);
            }
            partnerPara.add(new Chunk(periode + "\n", valueFont));
        }
        
        partnerPara.setSpacingAfter(15);
        document.add(partnerPara);
    }
    
    private void addSummarySection(Document document, PartnerSituationResponse situation) throws DocumentException {
        PartnerSituationResponse.Totaux totaux = situation.getTotaux();
        
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        
        PdfPTable summaryTable = new PdfPTable(4);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2f, 2f, 2f, 2f});
        summaryTable.setSpacingAfter(15);
        
        // En-tête
        PdfPCell headerCell = new PdfPCell(new Phrase("RÉCAPITULATIF", titleFont));
        headerCell.setColspan(4);
        headerCell.setBackgroundColor(BLUE_DARK);
        headerCell.setPadding(8);
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        headerCell.setTextColor(Color.WHITE);
        summaryTable.addCell(headerCell);
        
        // Lignes de totaux
        addSummaryRow(summaryTable, "Total Facturé TTC", formatAmount(totaux.getTotalFactureTTC()), labelFont, valueFont);
        addSummaryRow(summaryTable, "Total Payé", formatAmount(totaux.getTotalPaye()), labelFont, valueFont);
        addSummaryRow(summaryTable, "Total Restant", formatAmount(totaux.getTotalRestant()), labelFont, valueFont);
        addSummaryRow(summaryTable, "Solde", formatAmount(totaux.getSolde()), labelFont, valueFont);
        
        // Statistiques
        addSummaryRow(summaryTable, "Nombre Factures", String.valueOf(totaux.getNombreFactures()), labelFont, valueFont);
        addSummaryRow(summaryTable, "Payées", String.valueOf(totaux.getNombreFacturesPayees()), labelFont, valueFont);
        addSummaryRow(summaryTable, "En Attente", String.valueOf(totaux.getNombreFacturesEnAttente()), labelFont, valueFont);
        addSummaryRow(summaryTable, "En Retard", String.valueOf(totaux.getNombreFacturesEnRetard()), labelFont, valueFont);
        
        document.add(summaryTable);
    }
    
    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(GRAY_LIGHT);
        labelCell.setPadding(5);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
        
        // Cellules vides pour les colonnes 3 et 4
        table.addCell(new PdfPCell());
        table.addCell(new PdfPCell());
    }
    
    private void addFacturesTable(Document document, PartnerSituationResponse situation) throws DocumentException {
        if (situation.getFactures() == null || situation.getFactures().isEmpty()) {
            return;
        }
        
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        
        Paragraph title = new Paragraph("DÉTAIL DES FACTURES", titleFont);
        title.setSpacingBefore(10);
        title.setSpacingAfter(5);
        document.add(title);
        
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 0.8f});
        table.setSpacingAfter(15);
        
        // En-tête
        String[] headers = {"N° Facture", "Date", "Échéance", "Montant TTC", "Payé", "Restant", "Statut", "Avoir"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_DARK);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        
        // Lignes de données
        for (PartnerSituationResponse.FactureDetail facture : situation.getFactures()) {
            table.addCell(createCell(facture.getNumeroFacture() != null ? facture.getNumeroFacture() : "", cellFont));
            table.addCell(createCell(facture.getDateFacture() != null ? facture.getDateFacture().format(DATE_FORMATTER) : "", cellFont));
            table.addCell(createCell(facture.getDateEcheance() != null ? facture.getDateEcheance().format(DATE_FORMATTER) : "", cellFont));
            table.addCell(createCell(formatAmount(facture.getMontantTTC()), cellFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatAmount(facture.getMontantPaye()), cellFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatAmount(facture.getMontantRestant()), cellFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatStatut(facture.getStatut()), cellFont, Element.ALIGN_CENTER));
            table.addCell(createCell(facture.getEstAvoir() != null && facture.getEstAvoir() ? "Oui" : "Non", cellFont, Element.ALIGN_CENTER));
        }
        
        document.add(table);
    }
    
    private void addPrevisionsTable(Document document, PartnerSituationResponse situation) throws DocumentException {
        if (situation.getPrevisions() == null || situation.getPrevisions().isEmpty()) {
            return;
        }
        
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        
        Paragraph title = new Paragraph("PRÉVISIONS DE PAIEMENT", titleFont);
        title.setSpacingBefore(10);
        title.setSpacingAfter(5);
        document.add(title);
        
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f});
        table.setSpacingAfter(15);
        
        // En-tête
        String[] headers = {"N° Facture", "Date Prévue", "Montant Prévu", "Payé", "Restant", "Statut"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_DARK);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        
        // Lignes de données
        for (PartnerSituationResponse.PrevisionDetail prevision : situation.getPrevisions()) {
            table.addCell(createCell(prevision.getNumeroFacture() != null ? prevision.getNumeroFacture() : "", cellFont));
            table.addCell(createCell(prevision.getDatePrevue() != null ? prevision.getDatePrevue().format(DATE_FORMATTER) : "", cellFont));
            table.addCell(createCell(formatAmount(prevision.getMontantPrevu()), cellFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatAmount(prevision.getMontantPaye()), cellFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatAmount(prevision.getMontantRestant()), cellFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatStatut(prevision.getStatut()), cellFont, Element.ALIGN_CENTER));
        }
        
        document.add(table);
    }
    
    private void addFooter(Document document) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Paragraph footer = new Paragraph("Document généré le " + LocalDate.now().format(DATE_FORMATTER) + 
                " - STE BF4 INVEST", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }
    
    private PdfPCell createCell(String text, Font font) {
        return createCell(text, font, Element.ALIGN_LEFT);
    }
    
    private PdfPCell createCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }
    
    private String formatAmount(Double amount) {
        if (amount == null) return "0,00";
        return NUMBER_FORMAT.format(amount) + " MAD";
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
}

