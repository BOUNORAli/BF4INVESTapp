package com.bf4invest.pdf.helper;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

/**
 * Helper pour les opérations communes sur les documents PDF
 * Centralise les méthodes utilitaires pour créer des éléments PDF réutilisables
 */
public class PdfDocumentHelper {
    
    /**
     * Crée un document A4 avec des marges standard
     */
    public static Document createA4Document() {
        return new Document(PageSize.A4, 40f, 40f, 80f, 70f);
    }
    
    /**
     * Ajoute une cellule à un tableau avec le texte et l'alignement spécifiés
     */
    public static void addTableCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : ""));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOX);
        table.addCell(cell);
    }
    
    /**
     * Ajoute une cellule avec couleur de fond
     */
    public static void addTableCell(PdfPTable table, String text, int alignment, java.awt.Color backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : ""));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOX);
        cell.setBackgroundColor(backgroundColor);
        table.addCell(cell);
    }
    
    /**
     * Crée un paragraphe avec le texte et la police spécifiés
     */
    public static Paragraph createParagraph(String text, Font font) {
        return new Paragraph(text != null ? text : "", font);
    }
    
    /**
     * Crée un paragraphe avec le texte et la taille de police
     */
    public static Paragraph createParagraph(String text, float fontSize) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, fontSize);
        return createParagraph(text, font);
    }
    
    /**
     * Ajoute un espace vertical dans le document
     */
    public static void addVerticalSpace(Document document, float space) throws DocumentException {
        document.add(new Paragraph(" "));
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(space);
        document.add(spacer);
    }
    
    private PdfDocumentHelper() {
        // Utility class
    }
}

