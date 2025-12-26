package com.bf4invest.pdf.event;

import com.bf4invest.pdf.helper.PdfColorHelper;
import com.bf4invest.pdf.helper.PdfLogoHelper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.IOException;

/**
 * Page event pour ajouter le footer et le logo sur toutes les pages d'un BC
 */
@Slf4j
public class BCFooterPageEvent extends PdfPageEventHelper {
    
    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        try {
            PdfContentByte canvas = writer.getDirectContent();
            
            // Ajouter le logo en haut à gauche sur toutes les pages (sauf la première)
            if (writer.getPageNumber() > 1) {
                addLogoToPage(canvas, writer, document);
            }
            
            // Ajouter le footer en bas de page
            PdfPTable footerTable = new PdfPTable(1);
            float tableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            footerTable.setTotalWidth(tableWidth);
            footerTable.setLockedWidth(true);
            
            PdfPCell footerCell = new PdfPCell();
            footerCell.setBackgroundColor(PdfColorHelper.BLUE_LIGHT);
            footerCell.setPadding(10);
            footerCell.setBorder(Rectangle.NO_BORDER);
            footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            addCompanyFooterParagraphs(footerCell, footerFont);
            
            footerTable.addCell(footerCell);
            
            // Positionner le footer en bas de page
            float yPosition = document.bottomMargin() + 2f;
            footerTable.writeSelectedRows(0, -1, document.leftMargin(), yPosition, canvas);
        } catch (DocumentException | IOException e) {
            log.error("Error adding BC footer/logo to page", e);
        }
    }
    
    private void addLogoToPage(PdfContentByte canvas, PdfWriter writer, Document document) throws DocumentException, IOException {
        try {
            float logoWidth = 100f;
            float logoHeight = 75f;
            float xPosition = document.leftMargin();
            float yPosition = document.getPageSize().getHeight() - 2f;
            
            PdfPTable logoTable = new PdfPTable(1);
            logoTable.setTotalWidth(logoWidth);
            logoTable.setLockedWidth(true);
            
            PdfPCell logoCell = createLogoCell(writer, logoWidth, logoHeight);
            logoTable.addCell(logoCell);
            
            logoTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
        } catch (Exception e) {
            log.error("Error adding logo to page", e);
        }
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
            logoCell.addElement(new Paragraph("BF4\nINVEST", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PdfColorHelper.BLUE_DARK)));
        }
        
        return logoCell;
    }
    
    private void addCompanyFooterParagraphs(PdfPCell footerCell, Font footerFont) {
        Paragraph footer1 = new Paragraph("ICE: 002889872000062", footerFont);
        footer1.setAlignment(Element.ALIGN_CENTER);
        footer1.setSpacingAfter(3);
        
        Paragraph footer2 = new Paragraph("BF4 INVEST SARL au capital de 2.000.000,00 Dhs, Tel: 06 61 51 11 91", footerFont);
        footer2.setAlignment(Element.ALIGN_CENTER);
        footer2.setSpacingAfter(3);
        
        Paragraph footer3 = new Paragraph("RC de Meknes: 54287 - IF: 50499801 - TP: 17101980", footerFont);
        footer3.setAlignment(Element.ALIGN_CENTER);
        footer3.setSpacingAfter(0);
        
        footerCell.addElement(footer1);
        footerCell.addElement(footer2);
        footerCell.addElement(footer3);
    }
}

