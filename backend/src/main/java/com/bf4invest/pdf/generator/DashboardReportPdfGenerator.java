package com.bf4invest.pdf.generator;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.pdf.helper.PdfColorHelper;
import com.bf4invest.pdf.helper.PdfFormatHelper;
import com.bf4invest.pdf.helper.PdfLogoHelper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Générateur spécialisé pour les rapports PDF du dashboard
 * Extrait toute la logique de génération de rapport du PdfService monolithique
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardReportPdfGenerator {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color BLUE_DARK = PdfColorHelper.BLUE_DARK;
    private static final Color BLUE_LIGHT = PdfColorHelper.BLUE_LIGHT;
    private static final Color BLUE_HEADER = PdfColorHelper.BLUE_HEADER;
    private static final Color RED = PdfColorHelper.RED;
    
    /**
     * Génère le rapport PDF complet du dashboard
     */
    public byte[] generate(DashboardKpiResponse kpis, LocalDate from, LocalDate to, Double soldeActuel) 
            throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Page de garde avec table des matières
        addReportCoverPage(document, writer, from, to);
        document.newPage();
        
        // Table des matières
        addTableOfContents(document);
        document.newPage();
        
        // Sections existantes (1-8)
        addExecutiveSummary(document, kpis);
        addSoldeSection(document, soldeActuel);
        addTvaAnalysis(document, kpis);
        addImpayesSection(document, kpis);
        addMonthlyCaEvolution(document, kpis);
        addTopClients(document, kpis);
        addTopSuppliers(document, kpis);
        addAlertsSection(document, kpis);
        
        // Nouvelles sections (9-15)
        if (kpis.getChargeAnalysis() != null) {
            document.newPage();
            addChargesAnalysis(document, kpis.getChargeAnalysis());
        }
        
        if (kpis.getTreasuryForecast() != null) {
            document.newPage();
            addTreasuryForecast(document, kpis.getTreasuryForecast());
        }
        
        if (kpis.getPaymentAnalysis() != null) {
            document.newPage();
            addPaymentAnalysis(document, kpis.getPaymentAnalysis());
        }
        
        if (kpis.getProductPerformance() != null) {
            document.newPage();
            addProductPerformance(document, kpis.getProductPerformance());
        }
        
        if (kpis.getBcAnalysis() != null) {
            document.newPage();
            addBCAnalysis(document, kpis.getBcAnalysis());
        }
        
        if (kpis.getBalanceHistory() != null) {
            document.newPage();
            addBalanceHistory(document, kpis.getBalanceHistory());
        }
        
        if (kpis.getAdvancedCharts() != null) {
            document.newPage();
            addAdvancedCharts(document, kpis.getAdvancedCharts());
        }
        
        // Footer
        addReportFooter(document);
        
        document.close();
        return baos.toByteArray();
    }
    
    // ============ PAGE DE GARDE ============
    
    private void addReportCoverPage(Document document, PdfWriter writer, LocalDate from, LocalDate to) 
            throws DocumentException, IOException {
        // Logo
        PdfPTable logoTable = new PdfPTable(1);
        logoTable.setWidthPercentage(100);
        PdfPCell logoCell = PdfLogoHelper.createLogoCell(writer, 150f, 80f);
        logoCell.setPaddingBottom(20f);
        logoTable.addCell(logoCell);
        document.add(logoTable);
        
        // Titre principal
        Paragraph title = new Paragraph("RAPPORT D'ACTIVITÉ", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, Color.BLACK));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30f);
        document.add(title);
        
        // Période
        String periodStr = "Période: ";
        if (from != null && to != null) {
            periodStr += from.format(DATE_FORMATTER) + " au " + to.format(DATE_FORMATTER);
        } else if (from != null) {
            periodStr += "À partir du " + from.format(DATE_FORMATTER);
        } else if (to != null) {
            periodStr += "Jusqu'au " + to.format(DATE_FORMATTER);
        } else {
            periodStr += "Toutes périodes confondues";
        }
        
        Paragraph period = new Paragraph(periodStr, 
            FontFactory.getFont(FontFactory.HELVETICA, 14, Color.DARK_GRAY));
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(10f);
        document.add(period);
        
        // Date de génération
        String generatedDate = "Généré le: " + LocalDate.now().format(DATE_FORMATTER) + 
                               " à " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Paragraph generated = new Paragraph(generatedDate, 
            FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY));
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingAfter(50f);
        document.add(generated);
    }
    
    // ============ TABLE DES MATIÈRES ============
    
    private void addTableOfContents(Document document) throws DocumentException {
        Paragraph tocTitle = new Paragraph("TABLE DES MATIÈRES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BLUE_DARK));
        tocTitle.setAlignment(Element.ALIGN_CENTER);
        tocTitle.setSpacingAfter(30f);
        document.add(tocTitle);
        
        PdfPTable tocTable = new PdfPTable(2);
        tocTable.setWidthPercentage(100);
        tocTable.setWidths(new float[]{1f, 9f});
        tocTable.setSpacingAfter(5f);
        
        String[] sections = {
            "1. Synthèse Exécutive",
            "2. Situation de Trésorerie",
            "3. Analyse TVA",
            "4. Situation des Impayés",
            "5. Évolution Mensuelle du CA",
            "6. Top 10 Clients",
            "7. Top 10 Fournisseurs",
            "8. Alertes et Actions Requises",
            "9. Analyse des Charges",
            "10. Prévisions de Trésorerie",
            "11. Analyse des Paiements",
            "12. Performance Produits",
            "13. Analyse des Commandes",
            "14. Historique Détaillé Trésorerie",
            "15. Graphiques Avancés et Tendances"
        };
        
        for (int i = 0; i < sections.length; i++) {
            PdfPCell numCell = new PdfPCell(new Paragraph(String.valueOf(i + 1), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            numCell.setBorder(Rectangle.NO_BORDER);
            numCell.setPadding(5f);
            tocTable.addCell(numCell);
            
            PdfPCell titleCell = new PdfPCell(new Paragraph(sections[i], 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setPadding(5f);
            tocTable.addCell(titleCell);
        }
        
        document.add(tocTable);
    }
    
    // ============ SECTIONS EXISTANTES (1-8) ============
    
    private void addExecutiveSummary(Document document, DashboardKpiResponse kpis) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("1. SYNTHÈSE EXÉCUTIVE", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2.5f, 2.5f});
        summaryTable.setSpacingAfter(10f);
        
        boolean isEven = false;
        addSummaryRowStyled(summaryTable, "Chiffre d'Affaires HT:", formatCurrency(kpis.getCaHT()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Chiffre d'Affaires TTC:", formatCurrency(kpis.getCaTTC()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Total Achats HT:", formatCurrency(kpis.getTotalAchatsHT()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Total Achats TTC:", formatCurrency(kpis.getTotalAchatsTTC()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Marge Nette:", formatCurrency(kpis.getMargeTotale()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Marge Moyenne:", String.format("%.2f%%", kpis.getMargeMoyenne()), isEven);
        
        document.add(summaryTable);
        document.add(new Paragraph(" "));
    }
    
    private void addSoldeSection(Document document, Double soldeActuel) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("2. SITUATION DE TRÉSORERIE", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable soldeTable = new PdfPTable(2);
        soldeTable.setWidthPercentage(100);
        soldeTable.setWidths(new float[]{2f, 3f});
        soldeTable.setSpacingAfter(15f);
        
        PdfPCell labelCell = new PdfPCell(new Paragraph("Solde Global Actuel:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(15f);
        labelCell.setBackgroundColor(BLUE_LIGHT);
        soldeTable.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(soldeActuel != null ? soldeActuel : 0.0), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, 
                (soldeActuel != null && soldeActuel >= 0) ? new Color(0, 128, 0) : RED)));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(15f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor((soldeActuel != null && soldeActuel >= 0) ? 
            new Color(240, 255, 240) : new Color(255, 240, 240));
        soldeTable.addCell(valueCell);
        
        document.add(soldeTable);
        
        Paragraph status = new Paragraph(
            (soldeActuel != null && soldeActuel >= 0) ? 
                "✓ Trésorerie excédentaire" : "⚠ Trésorerie déficitaire",
            FontFactory.getFont(FontFactory.HELVETICA, 11, 
                (soldeActuel != null && soldeActuel >= 0) ? new Color(0, 128, 0) : RED));
        status.setSpacingBefore(5f);
        status.setSpacingAfter(15f);
        document.add(status);
        
        document.add(new Paragraph(" "));
    }
    
    private void addTvaAnalysis(Document document, DashboardKpiResponse kpis) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("3. ANALYSE TVA", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable tvaTable = new PdfPTable(2);
        tvaTable.setWidthPercentage(100);
        tvaTable.setWidths(new float[]{2.5f, 2.5f});
        tvaTable.setSpacingAfter(10f);
        
        addSummaryRowStyled(tvaTable, "TVA Collectée:", formatCurrency(kpis.getTvaCollectee()), false);
        addSummaryRowStyled(tvaTable, "TVA Déductible:", formatCurrency(kpis.getTvaDeductible()), true);
        
        double soldeTva = kpis.getTvaCollectee() - kpis.getTvaDeductible();
        PdfPCell labelCell = new PdfPCell(new Paragraph("Solde TVA:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(12f);
        labelCell.setBackgroundColor(BLUE_LIGHT);
        tvaTable.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(soldeTva), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, 
                soldeTva < 0 ? RED : new Color(0, 128, 0))));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(12f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (soldeTva < 0) {
            valueCell.setBackgroundColor(new Color(255, 240, 240));
        } else {
            valueCell.setBackgroundColor(new Color(240, 255, 240));
        }
        tvaTable.addCell(valueCell);
        
        document.add(tvaTable);
        document.add(new Paragraph(" "));
    }
    
    private void addImpayesSection(Document document, DashboardKpiResponse kpis) throws DocumentException {
        if (kpis.getImpayes() == null) return;
        
        Paragraph sectionTitle = new Paragraph("4. SITUATION DES IMPAYÉS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable impayesTable = new PdfPTable(3);
        impayesTable.setWidthPercentage(100);
        impayesTable.setWidths(new float[]{2.5f, 2f, 1f});
        impayesTable.setSpacingAfter(10f);
        
        addTableHeaderCell(impayesTable, "Tranche", true);
        addTableHeaderCell(impayesTable, "Montant", true);
        addTableHeaderCell(impayesTable, "%", true);
        
        double totalImpayes = kpis.getImpayes().getTotalImpayes();
        double impayes0_30 = kpis.getImpayes().getImpayes0_30();
        double impayes31_60 = kpis.getImpayes().getImpayes31_60();
        double impayesPlus60 = kpis.getImpayes().getImpayesPlus60();
        
        addImpayesRowStyled(impayesTable, "0-30 jours", impayes0_30, totalImpayes, false);
        addImpayesRowStyled(impayesTable, "31-60 jours", impayes31_60, totalImpayes, true);
        addImpayesRowStyled(impayesTable, "60+ jours", impayesPlus60, totalImpayes, false);
        
        // Total row
        PdfPCell totalLabelCell = new PdfPCell(new Paragraph("TOTAL", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        totalLabelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalLabelCell.setPadding(8f);
        totalLabelCell.setBackgroundColor(BLUE_LIGHT);
        impayesTable.addCell(totalLabelCell);
        
        PdfPCell totalAmountCell = new PdfPCell(new Paragraph(formatCurrency(totalImpayes), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        totalAmountCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalAmountCell.setPadding(8f);
        totalAmountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalAmountCell.setBackgroundColor(BLUE_LIGHT);
        impayesTable.addCell(totalAmountCell);
        
        PdfPCell totalPercentCell = new PdfPCell(new Paragraph("100%", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        totalPercentCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalPercentCell.setPadding(8f);
        totalPercentCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalPercentCell.setBackgroundColor(BLUE_LIGHT);
        impayesTable.addCell(totalPercentCell);
        
        document.add(impayesTable);
        document.add(new Paragraph(" "));
    }
    
    private void addMonthlyCaEvolution(Document document, DashboardKpiResponse kpis) throws DocumentException {
        if (kpis.getCaMensuel() == null || kpis.getCaMensuel().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("5. ÉVOLUTION MENSUELLE DU CA", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        List<DashboardKpiResponse.MonthlyData> monthlyData = kpis.getCaMensuel();
        if (monthlyData.size() > 12) {
            monthlyData = monthlyData.subList(monthlyData.size() - 12, monthlyData.size());
        }
        
        double maxCA = monthlyData.stream()
            .mapToDouble(DashboardKpiResponse.MonthlyData::getCaHT)
            .max()
            .orElse(1.0);
        
        PdfPTable monthlyTable = new PdfPTable(4);
        monthlyTable.setWidthPercentage(100);
        monthlyTable.setWidths(new float[]{1.5f, 2f, 1.5f, 1f});
        monthlyTable.setSpacingAfter(10f);
        
        addTableHeaderCell(monthlyTable, "Mois", true);
        addTableHeaderCell(monthlyTable, "Évolution", true);
        addTableHeaderCell(monthlyTable, "CA HT", true);
        addTableHeaderCell(monthlyTable, "Marge %", true);
        
        boolean isEven = false;
        for (DashboardKpiResponse.MonthlyData month : monthlyData) {
            PdfPCell monthCell = new PdfPCell(new Paragraph(formatMonth(month.getMois()), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            monthCell.setBorder(Rectangle.BOX);
            monthCell.setBorderColor(Color.GRAY);
            monthCell.setPadding(8f);
            monthCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
            monthlyTable.addCell(monthCell);
            
            PdfPCell barCell = createBarChartCell(month.getCaHT(), maxCA, isEven);
            monthlyTable.addCell(barCell);
            
            PdfPCell caCell = new PdfPCell(new Paragraph(formatCurrency(month.getCaHT()), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            caCell.setBorder(Rectangle.BOX);
            caCell.setBorderColor(Color.GRAY);
            caCell.setPadding(8f);
            caCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            caCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
            monthlyTable.addCell(caCell);
            
            PdfPCell margeCell = new PdfPCell(new Paragraph(String.format("%.1f%%", month.getMarge()), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            margeCell.setBorder(Rectangle.BOX);
            margeCell.setBorderColor(Color.GRAY);
            margeCell.setPadding(8f);
            margeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            margeCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
            monthlyTable.addCell(margeCell);
            
            isEven = !isEven;
        }
        
        document.add(monthlyTable);
        document.add(new Paragraph(" "));
    }
    
    private void addTopClients(Document document, DashboardKpiResponse kpis) throws DocumentException {
        if (kpis.getTopClients() == null || kpis.getTopClients().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("6. TOP 10 CLIENTS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable clientsTable = new PdfPTable(2);
        clientsTable.setWidthPercentage(100);
        clientsTable.setWidths(new float[]{3f, 2f});
        clientsTable.setSpacingAfter(10f);
        
        addTableHeaderCell(clientsTable, "Client", true);
        addTableHeaderCell(clientsTable, "Montant", true);
        
        List<DashboardKpiResponse.FournisseurClientStat> topClients = kpis.getTopClients();
        if (topClients.size() > 10) {
            topClients = topClients.subList(0, 10);
        }
        
        boolean isEven = false;
        for (DashboardKpiResponse.FournisseurClientStat client : topClients) {
            addTableCellStyled(clientsTable, client.getNom(), false, isEven);
            addTableCellStyled(clientsTable, formatCurrency(client.getMontant()), true, isEven);
            isEven = !isEven;
        }
        
        document.add(clientsTable);
        document.add(new Paragraph(" "));
    }
    
    private void addTopSuppliers(Document document, DashboardKpiResponse kpis) throws DocumentException {
        if (kpis.getTopFournisseurs() == null || kpis.getTopFournisseurs().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("7. TOP 10 FOURNISSEURS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable suppliersTable = new PdfPTable(2);
        suppliersTable.setWidthPercentage(100);
        suppliersTable.setWidths(new float[]{3f, 2f});
        suppliersTable.setSpacingAfter(10f);
        
        addTableHeaderCell(suppliersTable, "Fournisseur", true);
        addTableHeaderCell(suppliersTable, "Montant", true);
        
        List<DashboardKpiResponse.FournisseurClientStat> topSuppliers = kpis.getTopFournisseurs();
        if (topSuppliers.size() > 10) {
            topSuppliers = topSuppliers.subList(0, 10);
        }
        
        boolean isEven = false;
        for (DashboardKpiResponse.FournisseurClientStat supplier : topSuppliers) {
            addTableCellStyled(suppliersTable, supplier.getNom(), false, isEven);
            addTableCellStyled(suppliersTable, formatCurrency(supplier.getMontant()), true, isEven);
            isEven = !isEven;
        }
        
        document.add(suppliersTable);
        document.add(new Paragraph(" "));
    }
    
    private void addAlertsSection(Document document, DashboardKpiResponse kpis) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("8. ALERTES ET ACTIONS REQUISES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, RED));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable alertsTable = new PdfPTable(2);
        alertsTable.setWidthPercentage(100);
        alertsTable.setWidths(new float[]{2.5f, 2.5f});
        alertsTable.setSpacingAfter(10f);
        
        addSummaryRowStyled(alertsTable, "Factures en retard:", 
            String.valueOf(kpis.getFacturesEnRetard()), false);
        
        if (kpis.getImpayes() != null) {
            addSummaryRowStyled(alertsTable, "Montant total impayé:", 
                formatCurrency(kpis.getImpayes().getTotalImpayes()), true);
        }
        
        document.add(alertsTable);
    }
    
    // ============ NOUVELLES SECTIONS (9-15) ============
    // Ces méthodes seront implémentées dans la suite...
    
    private void addChargesAnalysis(Document document, DashboardKpiResponse.ChargeAnalysis charges) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("9. ANALYSE DES CHARGES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Résumé
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2.5f, 2.5f});
        summaryTable.setSpacingAfter(15f);
        
        addSummaryRowStyled(summaryTable, "Total Charges:", formatCurrency(charges.getTotalCharges()), false);
        addSummaryRowStyled(summaryTable, "Charges Prévues:", formatCurrency(charges.getChargesPrevues()), true);
        addSummaryRowStyled(summaryTable, "Charges Payées:", formatCurrency(charges.getChargesPayees()), false);
        
        document.add(summaryTable);
        
        // Répartition par catégorie
        if (charges.getRepartitionParCategorie() != null && !charges.getRepartitionParCategorie().isEmpty()) {
            Paragraph subTitle = new Paragraph("Répartition par Catégorie", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable catTable = new PdfPTable(3);
            catTable.setWidthPercentage(100);
            catTable.setWidths(new float[]{2.5f, 2f, 1f});
            catTable.setSpacingAfter(10f);
            
            addTableHeaderCell(catTable, "Catégorie", true);
            addTableHeaderCell(catTable, "Montant", true);
            addTableHeaderCell(catTable, "%", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.ChargeCategoryStat cat : charges.getRepartitionParCategorie()) {
                addTableCellStyled(catTable, cat.getCategorie(), false, isEven);
                addTableCellStyled(catTable, formatCurrency(cat.getMontant()), true, isEven);
                addTableCellStyled(catTable, String.format("%.1f%%", cat.getPourcentage()), true, isEven);
                isEven = !isEven;
            }
            
            document.add(catTable);
        }
        
        // Échéances
        if (charges.getEcheances() != null && !charges.getEcheances().isEmpty()) {
            Paragraph subTitle = new Paragraph("Échéancier des Charges", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable echeanceTable = new PdfPTable(3);
            echeanceTable.setWidthPercentage(100);
            echeanceTable.setWidths(new float[]{2f, 2f, 1f});
            echeanceTable.setSpacingAfter(10f);
            
            addTableHeaderCell(echeanceTable, "Période", true);
            addTableHeaderCell(echeanceTable, "Montant", true);
            addTableHeaderCell(echeanceTable, "Nombre", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.ChargeEcheance echeance : charges.getEcheances()) {
                addTableCellStyled(echeanceTable, echeance.getPeriode(), false, isEven);
                addTableCellStyled(echeanceTable, formatCurrency(echeance.getMontant()), true, isEven);
                addTableCellStyled(echeanceTable, String.valueOf(echeance.getNombre()), true, isEven);
                isEven = !isEven;
            }
            
            document.add(echeanceTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    private void addTreasuryForecast(Document document, DashboardKpiResponse.TreasuryForecast forecast) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("10. PRÉVISIONS DE TRÉSORERIE", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Solde actuel
        PdfPTable soldeTable = new PdfPTable(2);
        soldeTable.setWidthPercentage(100);
        soldeTable.setWidths(new float[]{2f, 3f});
        soldeTable.setSpacingAfter(15f);
        
        PdfPCell labelCell = new PdfPCell(new Paragraph("Solde Actuel:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(10f);
        labelCell.setBackgroundColor(BLUE_LIGHT);
        soldeTable.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(forecast.getSoldeActuel()), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(10f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        soldeTable.addCell(valueCell);
        
        document.add(soldeTable);
        
        // Graphique temporel (simplifié - tableau avec barres)
        if (forecast.getPrevisions3Mois() != null && !forecast.getPrevisions3Mois().isEmpty()) {
            Paragraph subTitle = new Paragraph("Évolution Prévisionnelle (3 mois)", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            // Limiter à 12 semaines pour la lisibilité
            List<DashboardKpiResponse.ForecastData> previsions = forecast.getPrevisions3Mois();
            if (previsions.size() > 84) { // Plus de 3 mois de jours
                previsions = previsions.stream()
                        .filter(p -> previsions.indexOf(p) % 7 == 0) // Une semaine sur une
                        .limit(12)
                        .collect(java.util.stream.Collectors.toList());
            } else if (previsions.size() > 30) {
                previsions = previsions.stream()
                        .filter(p -> previsions.indexOf(p) % 2 == 0) // Un jour sur deux
                        .limit(30)
                        .collect(java.util.stream.Collectors.toList());
            }
            
            double maxSolde = previsions.stream()
                    .mapToDouble(DashboardKpiResponse.ForecastData::getSoldePrevu)
                    .max()
                    .orElse(1.0);
            double minSolde = previsions.stream()
                    .mapToDouble(DashboardKpiResponse.ForecastData::getSoldePrevu)
                    .min()
                    .orElse(0.0);
            double range = Math.max(maxSolde - minSolde, 1.0);
            
            PdfPTable forecastTable = new PdfPTable(4);
            forecastTable.setWidthPercentage(100);
            forecastTable.setWidths(new float[]{1.5f, 2f, 1.5f, 1f});
            forecastTable.setSpacingAfter(10f);
            
            addTableHeaderCell(forecastTable, "Date", true);
            addTableHeaderCell(forecastTable, "Solde Prévu", true);
            addTableHeaderCell(forecastTable, "Encaissements", true);
            addTableHeaderCell(forecastTable, "Décaissements", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.ForecastData prev : previsions) {
                PdfPCell dateCell = new PdfPCell(new Paragraph(
                    prev.getDate().format(DATE_FORMATTER), 
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
                dateCell.setBorder(Rectangle.BOX);
                dateCell.setBorderColor(Color.GRAY);
                dateCell.setPadding(5f);
                dateCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                forecastTable.addCell(dateCell);
                
                // Barre graphique pour le solde
                PdfPCell barCell = createForecastBarCell(prev.getSoldePrevu(), minSolde, range, isEven);
                forecastTable.addCell(barCell);
                
                PdfPCell encaissementCell = new PdfPCell(new Paragraph(
                    formatCurrency(prev.getEncaissementsPrevu()), 
                    FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0, 128, 0))));
                encaissementCell.setBorder(Rectangle.BOX);
                encaissementCell.setBorderColor(Color.GRAY);
                encaissementCell.setPadding(5f);
                encaissementCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                encaissementCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                forecastTable.addCell(encaissementCell);
                
                PdfPCell decaissementCell = new PdfPCell(new Paragraph(
                    formatCurrency(prev.getDecaissementsPrevu()), 
                    FontFactory.getFont(FontFactory.HELVETICA, 9, RED)));
                decaissementCell.setBorder(Rectangle.BOX);
                decaissementCell.setBorderColor(Color.GRAY);
                decaissementCell.setPadding(5f);
                decaissementCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                decaissementCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                forecastTable.addCell(decaissementCell);
                
                isEven = !isEven;
            }
            
            document.add(forecastTable);
        }
        
        // Alertes
        if (forecast.getAlertes() != null && !forecast.getAlertes().isEmpty()) {
            Paragraph alertTitle = new Paragraph("Alertes", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, RED));
            alertTitle.setSpacingBefore(15f);
            alertTitle.setSpacingAfter(10f);
            document.add(alertTitle);
            
            for (String alerte : forecast.getAlertes()) {
                Paragraph alertPara = new Paragraph("⚠ " + alerte, 
                    FontFactory.getFont(FontFactory.HELVETICA, 10, RED));
                alertPara.setSpacingAfter(5f);
                document.add(alertPara);
            }
        }
        
        document.add(new Paragraph(" "));
    }
    
    private void addPaymentAnalysis(Document document, DashboardKpiResponse.PaymentAnalysis paymentAnalysis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("11. ANALYSE DES PAIEMENTS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Résumé
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2.5f, 2.5f});
        summaryTable.setSpacingAfter(15f);
        
        addSummaryRowStyled(summaryTable, "Total Encaissements:", formatCurrency(paymentAnalysis.getTotalEncaissements()), false);
        addSummaryRowStyled(summaryTable, "Total Décaissements:", formatCurrency(paymentAnalysis.getTotalDecaissements()), true);
        addSummaryRowStyled(summaryTable, "Délai Moyen Paiement Client:", 
            String.format("%.1f jours", paymentAnalysis.getDelaiMoyenPaiementClient()), false);
        addSummaryRowStyled(summaryTable, "Délai Moyen Paiement Fournisseur:", 
            String.format("%.1f jours", paymentAnalysis.getDelaiMoyenPaiementFournisseur()), true);
        addSummaryRowStyled(summaryTable, "DSO (Days Sales Outstanding):", 
            String.format("%.1f jours", paymentAnalysis.getDso()), false);
        addSummaryRowStyled(summaryTable, "DPO (Days Payable Outstanding):", 
            String.format("%.1f jours", paymentAnalysis.getDpo()), true);
        
        document.add(summaryTable);
        
        // Répartition par mode de paiement
        if (paymentAnalysis.getRepartitionParMode() != null && !paymentAnalysis.getRepartitionParMode().isEmpty()) {
            Paragraph subTitle = new Paragraph("Répartition par Mode de Paiement", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable modeTable = new PdfPTable(3);
            modeTable.setWidthPercentage(100);
            modeTable.setWidths(new float[]{2.5f, 2f, 1f});
            modeTable.setSpacingAfter(10f);
            
            addTableHeaderCell(modeTable, "Mode", true);
            addTableHeaderCell(modeTable, "Montant", true);
            addTableHeaderCell(modeTable, "%", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.PaymentModeStat mode : paymentAnalysis.getRepartitionParMode()) {
                addTableCellStyled(modeTable, mode.getMode(), false, isEven);
                addTableCellStyled(modeTable, formatCurrency(mode.getMontant()), true, isEven);
                addTableCellStyled(modeTable, String.format("%.1f%%", mode.getPourcentage()), true, isEven);
                isEven = !isEven;
            }
            
            document.add(modeTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    private void addProductPerformance(Document document, DashboardKpiResponse.ProductPerformance productPerf) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("12. PERFORMANCE PRODUITS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Top 10 par marge
        if (productPerf.getTop10ParMarge() != null && !productPerf.getTop10ParMarge().isEmpty()) {
            Paragraph subTitle = new Paragraph("Top 10 Produits par Marge", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable margeTable = new PdfPTable(4);
            margeTable.setWidthPercentage(100);
            margeTable.setWidths(new float[]{2.5f, 1.5f, 1.5f, 1f});
            margeTable.setSpacingAfter(10f);
            
            addTableHeaderCell(margeTable, "Produit", true);
            addTableHeaderCell(margeTable, "Marge", true);
            addTableHeaderCell(margeTable, "Volume", true);
            addTableHeaderCell(margeTable, "Marge %", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.ProductStat prod : productPerf.getTop10ParMarge()) {
                addTableCellStyled(margeTable, prod.getNom(), false, isEven);
                addTableCellStyled(margeTable, formatCurrency(prod.getMarge()), true, isEven);
                addTableCellStyled(margeTable, PdfFormatHelper.formatQuantity(prod.getVolume()), true, isEven);
                addTableCellStyled(margeTable, String.format("%.1f%%", prod.getMargePourcentage()), true, isEven);
                isEven = !isEven;
            }
            
            document.add(margeTable);
        }
        
        // Analyse ABC
        if (productPerf.getAnalyseABC() != null && !productPerf.getAnalyseABC().isEmpty()) {
            Paragraph subTitle = new Paragraph("Analyse ABC (80/20)", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable abcTable = new PdfPTable(4);
            abcTable.setWidthPercentage(100);
            abcTable.setWidths(new float[]{1f, 2f, 1.5f, 3.5f});
            abcTable.setSpacingAfter(10f);
            
            addTableHeaderCell(abcTable, "Catégorie", true);
            addTableHeaderCell(abcTable, "% CA", true);
            addTableHeaderCell(abcTable, "Nb Produits", true);
            addTableHeaderCell(abcTable, "Exemples", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.ProductABC abc : productPerf.getAnalyseABC()) {
                addTableCellStyled(abcTable, abc.getCategorie(), false, isEven);
                addTableCellStyled(abcTable, String.format("%.1f%%", abc.getPourcentageCA()), true, isEven);
                addTableCellStyled(abcTable, String.valueOf(abc.getNombreProduits()), true, isEven);
                String exemples = abc.getProduits() != null && !abc.getProduits().isEmpty() ?
                    String.join(", ", abc.getProduits().subList(0, Math.min(3, abc.getProduits().size()))) : "";
                addTableCellStyled(abcTable, exemples, false, isEven);
                isEven = !isEven;
            }
            
            document.add(abcTable);
        }
        
        // Alertes stock
        if (productPerf.getAlertesStock() != null && !productPerf.getAlertesStock().isEmpty()) {
            Paragraph subTitle = new Paragraph("Alertes Stock", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, RED));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable alertTable = new PdfPTable(3);
            alertTable.setWidthPercentage(100);
            alertTable.setWidths(new float[]{2.5f, 1.5f, 2f});
            alertTable.setSpacingAfter(10f);
            
            addTableHeaderCell(alertTable, "Produit", true);
            addTableHeaderCell(alertTable, "Type", true);
            addTableHeaderCell(alertTable, "Message", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.ProductAlert alert : productPerf.getAlertesStock()) {
                addTableCellStyled(alertTable, alert.getProduitNom(), false, isEven);
                addTableCellStyled(alertTable, alert.getType(), false, isEven);
                addTableCellStyled(alertTable, alert.getMessage(), false, isEven);
                isEven = !isEven;
            }
            
            document.add(alertTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    private void addBCAnalysis(Document document, DashboardKpiResponse.BCAnalysis bcAnalysis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("13. ANALYSE DES COMMANDES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Résumé
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2.5f, 2.5f});
        summaryTable.setSpacingAfter(15f);
        
        addSummaryRowStyled(summaryTable, "Total BCs:", String.valueOf(bcAnalysis.getTotalBCs()), false);
        addSummaryRowStyled(summaryTable, "BCs Brouillon:", String.valueOf(bcAnalysis.getBcsDraft()), true);
        addSummaryRowStyled(summaryTable, "BCs Envoyées:", String.valueOf(bcAnalysis.getBcsSent()), false);
        addSummaryRowStyled(summaryTable, "BCs Complétées:", String.valueOf(bcAnalysis.getBcsCompleted()), true);
        addSummaryRowStyled(summaryTable, "Délai Moyen Traitement:", 
            String.format("%.1f jours", bcAnalysis.getDelaiMoyenTraitement()), false);
        addSummaryRowStyled(summaryTable, "Taux Conversion BC→Facture:", 
            String.format("%.1f%%", bcAnalysis.getTauxConversionBCFacture()), true);
        
        document.add(summaryTable);
        
        // Performance par client
        if (bcAnalysis.getPerformanceParClient() != null && !bcAnalysis.getPerformanceParClient().isEmpty()) {
            Paragraph subTitle = new Paragraph("Performance par Client", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable perfTable = new PdfPTable(4);
            perfTable.setWidthPercentage(100);
            perfTable.setWidths(new float[]{2.5f, 1f, 1.5f, 1f});
            perfTable.setSpacingAfter(10f);
            
            addTableHeaderCell(perfTable, "Client", true);
            addTableHeaderCell(perfTable, "Nb BCs", true);
            addTableHeaderCell(perfTable, "Montant Total", true);
            addTableHeaderCell(perfTable, "Délai Moyen", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.BCPerformance perf : bcAnalysis.getPerformanceParClient()) {
                addTableCellStyled(perfTable, perf.getPartenaireNom(), false, isEven);
                addTableCellStyled(perfTable, String.valueOf(perf.getNombreBCs()), true, isEven);
                addTableCellStyled(perfTable, formatCurrency(perf.getMontantTotal()), true, isEven);
                addTableCellStyled(perfTable, String.format("%.1f j", perf.getDelaiMoyen()), true, isEven);
                isEven = !isEven;
            }
            
            document.add(perfTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    private void addBalanceHistory(Document document, DashboardKpiResponse.BalanceHistory balanceHistory) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("14. HISTORIQUE DÉTAILLÉ TRÉSORERIE", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Résumé
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2.5f, 2.5f});
        summaryTable.setSpacingAfter(15f);
        
        addSummaryRowStyled(summaryTable, "Solde Initial:", formatCurrency(balanceHistory.getSoldeInitial()), false);
        addSummaryRowStyled(summaryTable, "Solde Actuel:", formatCurrency(balanceHistory.getSoldeActuel()), true);
        
        document.add(summaryTable);
        
        // Mouvements récents (limiter à 30 pour la lisibilité)
        if (balanceHistory.getMouvements() != null && !balanceHistory.getMouvements().isEmpty()) {
            Paragraph subTitle = new Paragraph("Mouvements Récents", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            List<DashboardKpiResponse.BalanceMovement> mouvements = balanceHistory.getMouvements();
            if (mouvements.size() > 30) {
                mouvements = mouvements.subList(0, 30);
            }
            
            PdfPTable movementTable = new PdfPTable(5);
            movementTable.setWidthPercentage(100);
            movementTable.setWidths(new float[]{1.2f, 1.5f, 1.8f, 1.5f, 1f});
            movementTable.setSpacingAfter(10f);
            
            addTableHeaderCell(movementTable, "Date", true);
            addTableHeaderCell(movementTable, "Type", true);
            addTableHeaderCell(movementTable, "Partenaire", true);
            addTableHeaderCell(movementTable, "Montant", true);
            addTableHeaderCell(movementTable, "Solde", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.BalanceMovement mov : mouvements) {
                PdfPCell dateCell = new PdfPCell(new Paragraph(
                    mov.getDate().format(DATE_FORMATTER), 
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
                dateCell.setBorder(Rectangle.BOX);
                dateCell.setBorderColor(Color.GRAY);
                dateCell.setPadding(5f);
                dateCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                movementTable.addCell(dateCell);
                
                addTableCellStyled(movementTable, mov.getType(), false, isEven);
                addTableCellStyled(movementTable, mov.getPartenaire(), false, isEven);
                
                Color montantColor = mov.getMontant() >= 0 ? new Color(0, 128, 0) : RED;
                PdfPCell montantCell = new PdfPCell(new Paragraph(
                    formatCurrency(mov.getMontant()), 
                    FontFactory.getFont(FontFactory.HELVETICA, 9, montantColor)));
                montantCell.setBorder(Rectangle.BOX);
                montantCell.setBorderColor(Color.GRAY);
                montantCell.setPadding(5f);
                montantCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                montantCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                movementTable.addCell(montantCell);
                
                addTableCellStyled(movementTable, formatCurrency(mov.getSoldeApres()), true, isEven);
                
                isEven = !isEven;
            }
            
            document.add(movementTable);
        }
        
        // Solde par partenaire
        if (balanceHistory.getSoldeParPartenaire() != null && !balanceHistory.getSoldeParPartenaire().isEmpty()) {
            Paragraph subTitle = new Paragraph("Soldes par Partenaire", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable partnerTable = new PdfPTable(3);
            partnerTable.setWidthPercentage(100);
            partnerTable.setWidths(new float[]{2.5f, 1.5f, 2f});
            partnerTable.setSpacingAfter(10f);
            
            addTableHeaderCell(partnerTable, "Partenaire", true);
            addTableHeaderCell(partnerTable, "Type", true);
            addTableHeaderCell(partnerTable, "Solde", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.BalanceByPartner partner : balanceHistory.getSoldeParPartenaire()) {
                addTableCellStyled(partnerTable, partner.getPartenaireNom(), false, isEven);
                addTableCellStyled(partnerTable, partner.getPartenaireType(), false, isEven);
                
                Color soldeColor = partner.getSolde() >= 0 ? new Color(0, 128, 0) : RED;
                PdfPCell soldeCell = new PdfPCell(new Paragraph(
                    formatCurrency(partner.getSolde()), 
                    FontFactory.getFont(FontFactory.HELVETICA, 10, soldeColor)));
                soldeCell.setBorder(Rectangle.BOX);
                soldeCell.setBorderColor(Color.GRAY);
                soldeCell.setPadding(8f);
                soldeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                soldeCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                partnerTable.addCell(soldeCell);
                
                isEven = !isEven;
            }
            
            document.add(partnerTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    private void addAdvancedCharts(Document document, DashboardKpiResponse.AdvancedCharts advancedCharts) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("15. GRAPHIQUES AVANCÉS ET TENDANCES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Evolution des marges
        if (advancedCharts.getEvolutionMarges() != null && !advancedCharts.getEvolutionMarges().isEmpty()) {
            Paragraph subTitle = new Paragraph("Évolution des Marges", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable margeTable = new PdfPTable(3);
            margeTable.setWidthPercentage(100);
            margeTable.setWidths(new float[]{2f, 2f, 1f});
            margeTable.setSpacingAfter(10f);
            
            addTableHeaderCell(margeTable, "Mois", true);
            addTableHeaderCell(margeTable, "Marge", true);
            addTableHeaderCell(margeTable, "Marge %", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.MarginEvolution evo : advancedCharts.getEvolutionMarges()) {
                addTableCellStyled(margeTable, formatMonth(evo.getMois()), false, isEven);
                addTableCellStyled(margeTable, formatCurrency(evo.getMarge()), true, isEven);
                addTableCellStyled(margeTable, String.format("%.1f%%", evo.getMargePourcentage()), true, isEven);
                isEven = !isEven;
            }
            
            document.add(margeTable);
        }
        
        // Indicateurs de croissance
        if (advancedCharts.getIndicateursCroissance() != null) {
            Paragraph subTitle = new Paragraph("Indicateurs de Croissance", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable growthTable = new PdfPTable(2);
            growthTable.setWidthPercentage(100);
            growthTable.setWidths(new float[]{2.5f, 2.5f});
            growthTable.setSpacingAfter(10f);
            
            DashboardKpiResponse.GrowthIndicators growth = advancedCharts.getIndicateursCroissance();
            addSummaryRowStyled(growthTable, "Croissance MoM:", 
                String.format("%.2f%%", growth.getCroissanceMoM()), false);
            addSummaryRowStyled(growthTable, "Croissance YoY:", 
                String.format("%.2f%%", growth.getCroissanceYoY()), true);
            addSummaryRowStyled(growthTable, "Croissance Moyenne:", 
                String.format("%.2f%%", growth.getCroissanceMoyenne()), false);
            
            document.add(growthTable);
        }
        
        // Heatmap mensuelle
        if (advancedCharts.getHeatmapMensuelle() != null && !advancedCharts.getHeatmapMensuelle().isEmpty()) {
            Paragraph subTitle = new Paragraph("Heatmap Mensuelle des Performances", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK));
            subTitle.setSpacingBefore(15f);
            subTitle.setSpacingAfter(10f);
            document.add(subTitle);
            
            PdfPTable heatmapTable = new PdfPTable(3);
            heatmapTable.setWidthPercentage(100);
            heatmapTable.setWidths(new float[]{2f, 1.5f, 2.5f});
            heatmapTable.setSpacingAfter(10f);
            
            addTableHeaderCell(heatmapTable, "Mois", true);
            addTableHeaderCell(heatmapTable, "Score", true);
            addTableHeaderCell(heatmapTable, "Niveau", true);
            
            boolean isEven = false;
            for (DashboardKpiResponse.MonthlyPerformance perf : advancedCharts.getHeatmapMensuelle()) {
                addTableCellStyled(heatmapTable, formatMonth(perf.getMois()), false, isEven);
                addTableCellStyled(heatmapTable, String.format("%.1f", perf.getScore()), true, isEven);
                
                Color niveauColor = "EXCELLENT".equals(perf.getNiveau()) ? new Color(0, 128, 0) :
                    "BON".equals(perf.getNiveau()) ? new Color(0, 150, 255) :
                    "MOYEN".equals(perf.getNiveau()) ? new Color(255, 165, 0) : RED;
                
                PdfPCell niveauCell = new PdfPCell(new Paragraph(
                    perf.getNiveau(), 
                    FontFactory.getFont(FontFactory.HELVETICA, 10, niveauColor)));
                niveauCell.setBorder(Rectangle.BOX);
                niveauCell.setBorderColor(Color.GRAY);
                niveauCell.setPadding(8f);
                niveauCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                niveauCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
                heatmapTable.addCell(niveauCell);
                
                isEven = !isEven;
            }
            
            document.add(heatmapTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    // ============ HELPER METHODS ============
    
    private void addReportFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph("BF4 INVEST - Rapport généré automatiquement", 
            FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30f);
        document.add(footer);
    }
    
    private void addSummaryRowStyled(PdfPTable table, String label, String value, boolean isEven) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, 
            FontFactory.getFont(FontFactory.HELVETICA, 11)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(10f);
        labelCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(value, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(10f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        table.addCell(valueCell);
    }
    
    private void addTableHeaderCell(PdfPTable table, String text, boolean bold) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 11, Color.WHITE)));
        cell.setBackgroundColor(BLUE_DARK);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
    
    private void addTableCellStyled(PdfPTable table, String text, boolean rightAlign, boolean isEven) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(rightAlign ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        table.addCell(cell);
    }
    
    private void addImpayesRowStyled(PdfPTable table, String tranche, double montant, double total, boolean isEven) {
        addTableCellStyled(table, tranche, false, isEven);
        addTableCellStyled(table, formatCurrency(montant), true, isEven);
        double percent = total > 0 ? (montant / total) * 100 : 0;
        addTableCellStyled(table, String.format("%.1f%%", percent), true, isEven);
    }
    
    private PdfPCell createBarChartCell(double value, double maxValue, boolean isEven) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(5f);
        cell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        cell.setFixedHeight(25f);
        
        PdfPTable barTable = new PdfPTable(2);
        barTable.setWidthPercentage(100);
        barTable.setSpacingBefore(0f);
        barTable.setSpacingAfter(0f);
        
        double percentage = maxValue > 0 ? (value / maxValue) * 100 : 0;
        float barWidth = (float) Math.max(Math.min(percentage, 98), 2);
        float emptyWidth = 100f - barWidth;
        
        PdfPCell barColorCell = new PdfPCell();
        barColorCell.setBorder(Rectangle.NO_BORDER);
        barColorCell.setBackgroundColor(BLUE_HEADER);
        barColorCell.setFixedHeight(15f);
        barColorCell.setPadding(0f);
        barTable.addCell(barColorCell);
        
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        emptyCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        emptyCell.setFixedHeight(15f);
        emptyCell.setPadding(0f);
        barTable.addCell(emptyCell);
        
        barTable.setWidths(new float[]{barWidth, emptyWidth});
        cell.addElement(barTable);
        return cell;
    }
    
    private PdfPCell createForecastBarCell(double value, double minValue, double range, boolean isEven) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(5f);
        cell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        cell.setFixedHeight(20f);
        
        PdfPTable barTable = new PdfPTable(2);
        barTable.setWidthPercentage(100);
        
        double normalized = range > 0 ? ((value - minValue) / range) * 100 : 0;
        float barWidth = (float) Math.max(Math.min(normalized, 98), 2);
        float emptyWidth = 100f - barWidth;
        
        Color barColor = value >= 0 ? new Color(0, 128, 0) : RED;
        
        PdfPCell barColorCell = new PdfPCell();
        barColorCell.setBorder(Rectangle.NO_BORDER);
        barColorCell.setBackgroundColor(barColor);
        barColorCell.setFixedHeight(12f);
        barColorCell.setPadding(0f);
        barTable.addCell(barColorCell);
        
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        emptyCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        emptyCell.setFixedHeight(12f);
        emptyCell.setPadding(0f);
        barTable.addCell(emptyCell);
        
        barTable.setWidths(new float[]{barWidth, emptyWidth});
        cell.addElement(barTable);
        return cell;
    }
    
    private String formatCurrency(double amount) {
        return PdfFormatHelper.formatAmount(amount) + " MAD";
    }
    
    private String formatMonth(String monthStr) {
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr);
            return ym.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH));
        } catch (Exception e) {
            return monthStr;
        }
    }
}

