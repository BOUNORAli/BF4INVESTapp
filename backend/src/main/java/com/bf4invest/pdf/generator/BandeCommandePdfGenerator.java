package com.bf4invest.pdf.generator;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.Client;
import com.bf4invest.model.Supplier;
import com.bf4invest.pdf.event.BCFooterPageEvent;
import com.bf4invest.pdf.helper.*;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.SupplierRepository;
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
import java.time.LocalDate;

/**
 * Générateur spécialisé pour les PDFs de Bande de Commande
 * Extrait toute la logique de génération BC du PdfService monolithique
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BandeCommandePdfGenerator {
    
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    /**
     * Génère le PDF d'une bande de commande
     */
    public byte[] generate(BandeCommande bc) throws DocumentException, IOException {
        if (bc == null) {
            throw new IllegalArgumentException("BandeCommande cannot be null");
        }
        
        log.debug("Generating PDF for BC: id={}, numeroBC={}", bc.getId(), bc.getNumeroBC());
        
        Document document = PdfDocumentHelper.createA4Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        writer.setPageEvent(new BCFooterPageEvent());
        document.open();
        
        try {
            // Récupérer les informations client et fournisseur
            Client client = getClient(bc);
            Supplier supplier = getSupplier(bc);
            
            if (supplier == null) {
                log.warn("No supplier found for BC {} (fournisseurId: {})", bc.getId(), bc.getFournisseurId());
            }
            
            // Construire le document
            addHeader(document, bc, writer);
            addDestinataire(document, bc, supplier);
            addProductTable(document, bc);
            addDeliveryInfo(document, bc, client);
        } catch (Exception e) {
            log.error("Error generating PDF content for BC {}: {}", bc.getId(), e.getMessage(), e);
            throw e;
        } finally {
            document.close();
        }
        
        return baos.toByteArray();
    }
    
    private Client getClient(BandeCommande bc) {
        if (bc.getClientsVente() != null && !bc.getClientsVente().isEmpty()) {
            String firstClientId = bc.getClientsVente().get(0).getClientId();
            if (firstClientId != null) {
                return clientRepository.findById(firstClientId).orElse(null);
            }
        } else if (bc.getClientId() != null) {
            return clientRepository.findById(bc.getClientId()).orElse(null);
        }
        return null;
    }
    
    private Supplier getSupplier(BandeCommande bc) {
        return bc.getFournisseurId() != null ? 
            supplierRepository.findById(bc.getFournisseurId()).orElse(null) : null;
    }
    
    private void addHeader(Document document, BandeCommande bc, PdfWriter writer) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 8f});
        headerTable.setSpacingAfter(10);
        
        // Logo
        PdfPCell logoCell = createLogoCell(writer, 100f, 75f);
        logoCell.setVerticalAlignment(Element.ALIGN_TOP);
        headerTable.addCell(logoCell);
        
        // Titre + numéro
        PdfPCell contentCell = new PdfPCell();
        contentCell.setBorder(Rectangle.NO_BORDER);
        contentCell.setPadding(0);
        contentCell.setVerticalAlignment(Element.ALIGN_TOP);
        
        Paragraph title = new Paragraph();
        title.add(new Chunk("BON DE COMMANDE N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        String numeroBC = bc.getNumeroBC() != null ? bc.getNumeroBC() : "";
        title.add(new Chunk(" " + numeroBC, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PdfColorHelper.RED)));
        title.setAlignment(Element.ALIGN_CENTER);
        contentCell.addElement(title);
        
        headerTable.addCell(contentCell);
        document.add(headerTable);
    }
    
    private void addDestinataire(Document document, BandeCommande bc, Supplier supplier) throws DocumentException {
        PdfPTable destTable = new PdfPTable(3);
        destTable.setWidthPercentage(100);
        destTable.setSpacingAfter(15);
        destTable.setWidths(new float[]{2f, 4f, 4f});
        
        PdfPCell labelCell = new PdfPCell(new Phrase("DESTINATAIRE :", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        labelCell.setBackgroundColor(PdfColorHelper.BLUE_LIGHT);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.WHITE);
        labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        destTable.addCell(labelCell);
        
        PdfPCell nameCell = new PdfPCell();
        nameCell.setBorder(Rectangle.BOX);
        nameCell.setBorderColor(Color.WHITE);
        nameCell.setPadding(8);
        String supplierName = (supplier != null && supplier.getNom() != null) ? supplier.getNom() : 
            (bc.getFournisseurId() != null ? "Fournisseur ID: " + bc.getFournisseurId() : "Non spécifié");
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.UNDERLINE, PdfColorHelper.RED);
        nameCell.addElement(new Phrase(supplierName, nameFont));
        destTable.addCell(nameCell);
        
        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setPadding(8);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        LocalDate dateBC = bc.getDateBC() != null ? bc.getDateBC() : LocalDate.now();
        Paragraph dateLine = new Paragraph();
        dateLine.add(new Chunk("MEKNES LE: ", FontFactory.getFont(FontFactory.HELVETICA, 10)));
        dateLine.add(new Chunk(PdfFormatHelper.formatDate(dateBC), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PdfColorHelper.RED)));
        dateLine.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(dateLine);
        destTable.addCell(dateCell);
        
        document.add(destTable);
    }
    
    private void addProductTable(Document document, BandeCommande bc) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 3f, 1f, 1.5f, 2f, 2.5f});
        table.setSpacingAfter(15);
        
        // En-têtes
        String[] headers = {"N°", "Désignation", "Unité", "Quantité", "PU HT", "Prix Total HT"};
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PdfColorHelper.BLUE_HEADER);
        
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(PdfColorHelper.BLUE_LIGHT);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        
        double totalHT = 0.0;
        double tauxTVA = 20.0;
        boolean hasLines = false;
        
        // Nouvelle structure: lignesAchat
        if (bc.getLignesAchat() != null && !bc.getLignesAchat().isEmpty()) {
            hasLines = true;
            int lineNum = 1;
            for (var ligne : bc.getLignesAchat()) {
                if (ligne == null) {
                    log.warn("Null ligne found in lignesAchat for BC {}", bc.getId());
                    continue;
                }
                
                PdfDocumentHelper.addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                PdfDocumentHelper.addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                PdfDocumentHelper.addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatQuantity(ligne.getQuantiteAchetee()), 
                    Element.ALIGN_RIGHT);
                PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(ligne.getPrixAchatUnitaireHT()), 
                    Element.ALIGN_RIGHT);
                
                double lineTotalHT = (ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0) * 
                    (ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0);
                PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(lineTotalHT), Element.ALIGN_RIGHT);
                
                totalHT += lineTotalHT;
                if (ligne.getTva() != null && ligne.getTva() > 0) {
                    tauxTVA = ligne.getTva();
                }
            }
        }
        // Rétrocompatibilité: ancienne structure lignes
        else if (bc.getLignes() != null && !bc.getLignes().isEmpty()) {
            hasLines = true;
            int lineNum = 1;
            for (var ligne : bc.getLignes()) {
                if (ligne == null) {
                    log.warn("Null ligne found in lignes for BC {}", bc.getId());
                    continue;
                }
                
                PdfDocumentHelper.addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                PdfDocumentHelper.addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                PdfDocumentHelper.addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                // Convertir Integer en Double pour formatQuantity
                Double qtyValue = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee().doubleValue() : 0.0;
                PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatQuantity(qtyValue), 
                    Element.ALIGN_RIGHT);
                PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(ligne.getPrixAchatUnitaireHT()), 
                    Element.ALIGN_RIGHT);
                
                double lineTotalHT = (ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee().doubleValue() : 0) * 
                    (ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0);
                PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(lineTotalHT), Element.ALIGN_RIGHT);
                
                totalHT += lineTotalHT;
                if (ligne.getTva() != null && ligne.getTva() > 0) {
                    tauxTVA = ligne.getTva();
                }
            }
        }
        
        // Si aucune ligne trouvée, utiliser les totaux pré-calculés si disponibles
        if (!hasLines) {
            log.warn("No product lines found for BC {}, using pre-calculated totals if available", bc.getId());
            if (bc.getTotalAchatHT() != null) {
                totalHT = bc.getTotalAchatHT();
            }
        }
        
        // Totaux dans le tableau
        addTotalsRows(table, totalHT, tauxTVA);
        
        document.add(table);
    }
    
    private void addTotalsRows(PdfPTable table, double totalHT, double tauxTVA) {
        // Ligne vide
        for (int i = 0; i < 6; i++) {
            PdfDocumentHelper.addTableCell(table, "", Element.ALIGN_CENTER);
        }
        
        // Total HT
        PdfPCell totalHTCell = new PdfPCell(new Phrase("TOTAL HT", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        totalHTCell.setColspan(5);
        totalHTCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalHTCell.setPadding(6);
        totalHTCell.setBorder(Rectangle.BOX);
        totalHTCell.setBorderColor(Color.WHITE);
        table.addCell(totalHTCell);
        PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(totalHT), Element.ALIGN_RIGHT);
        
        // TVA
        double totalTVA = totalHT * (tauxTVA / 100.0);
        PdfPCell tvaCell = new PdfPCell(new Phrase("TVA " + tauxTVA + "%", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaCell.setColspan(5);
        tvaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tvaCell.setPadding(6);
        tvaCell.setBorder(Rectangle.BOX);
        tvaCell.setBorderColor(Color.WHITE);
        table.addCell(tvaCell);
        PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(totalTVA), Element.ALIGN_RIGHT);
        
        // Total TTC
        double totalTTC = totalHT + totalTVA;
        PdfPCell totalTTCCell = new PdfPCell(new Phrase("TOTAL TTC", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PdfColorHelper.RED)));
        totalTTCCell.setColspan(5);
        totalTTCCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTTCCell.setPadding(6);
        totalTTCCell.setBorder(Rectangle.BOX);
        totalTTCCell.setBorderColor(Color.WHITE);
        totalTTCCell.setBackgroundColor(PdfColorHelper.BLUE_LIGHT);
        table.addCell(totalTTCCell);
        PdfDocumentHelper.addTableCell(table, PdfFormatHelper.formatAmount(totalTTC), Element.ALIGN_RIGHT);
    }
    
    private void addDeliveryInfo(Document document, BandeCommande bc, Client client) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(10);
        
        // Livraison
        PdfPCell livraisonCell = new PdfPCell();
        livraisonCell.setBackgroundColor(PdfColorHelper.BLUE_LIGHT);
        livraisonCell.setPadding(8);
        livraisonCell.setBorder(Rectangle.BOX);
        Paragraph livraisonTitle = new Paragraph("LIVRAISON", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        livraisonCell.addElement(livraisonTitle);
        if (bc.getLieuLivraison() != null) {
            livraisonCell.addElement(new Paragraph(bc.getLieuLivraison(), 
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        }
        infoTable.addCell(livraisonCell);
        
        // Paiement
        PdfPCell paiementCell = new PdfPCell();
        paiementCell.setBackgroundColor(PdfColorHelper.BLUE_LIGHT);
        paiementCell.setPadding(8);
        paiementCell.setBorder(Rectangle.BOX);
        Paragraph paiementTitle = new Paragraph("PAIEMENT", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        paiementCell.addElement(paiementTitle);
        if (bc.getModePaiement() != null) {
            paiementCell.addElement(new Paragraph(bc.getModePaiement(), 
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        }
        infoTable.addCell(paiementCell);
        
        document.add(infoTable);
    }
    
    private PdfPCell createLogoCell(PdfWriter writer, float width, float height) throws DocumentException, IOException {
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBackgroundColor(Color.WHITE);
        logoCell.setFixedHeight(height);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(0);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        
        try {
            Image logoImage = PdfLogoHelper.loadLogo();
            if (logoImage != null) {
                float imgW = logoImage.getWidth();
                float imgH = logoImage.getHeight();
                float scale = Math.min((width - 4) / imgW, (height - 4) / imgH);
                logoImage.scaleAbsolute(imgW * scale, imgH * scale);
                logoCell.addElement(logoImage);
            } else {
                logoCell.addElement(new Paragraph("BF4\nINVEST", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PdfColorHelper.BLUE_DARK)));
            }
        } catch (IOException e) {
            log.warn("Erreur chargement logo, utilisation du fallback texte");
            logoCell.addElement(new Paragraph("BF4\nINVEST", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PdfColorHelper.BLUE_DARK)));
        }
        
        return logoCell;
    }
}

