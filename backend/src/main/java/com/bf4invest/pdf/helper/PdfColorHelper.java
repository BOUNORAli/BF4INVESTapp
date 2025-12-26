package com.bf4invest.pdf.helper;

import java.awt.Color;

/**
 * Helper pour les couleurs utilisées dans les PDFs
 * Centralise toutes les couleurs pour maintenir la cohérence visuelle
 */
public class PdfColorHelper {
    
    // Couleurs principales BF4 Invest
    public static final Color BLUE_DARK = new Color(30, 64, 124); // Bleu foncé pour logo
    public static final Color BLUE_LIGHT = new Color(200, 220, 240); // Bleu clair grisé pour les sections
    public static final Color BLUE_HEADER = new Color(70, 130, 180); // Bleu moyen pour les headers de tableau
    public static final Color RED = new Color(180, 0, 0); // Rouge foncé pour les éléments importants
    
    // Couleurs utilitaires
    public static final Color WHITE = Color.WHITE;
    public static final Color BLACK = Color.BLACK;
    public static final Color GRAY_LIGHT = new Color(245, 245, 245);
    public static final Color GRAY_MEDIUM = new Color(200, 200, 200);
    
    private PdfColorHelper() {
        // Utility class
    }
}

