package com.bf4invest.pdf.helper;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Helper pour le formatage des données dans les PDFs
 * Centralise tous les formats (montants, dates, quantités) selon les standards français
 */
public class PdfFormatHelper {
    
    // Format français: espace insécable pour milliers, virgule pour décimales (ex: 1 515,83)
    private static final NumberFormat FRENCH_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    static {
        FRENCH_NUMBER_FORMAT.setMinimumFractionDigits(2);
        FRENCH_NUMBER_FORMAT.setMaximumFractionDigits(2);
        FRENCH_NUMBER_FORMAT.setGroupingUsed(true);
    }
    
    private static final NumberFormat FRENCH_QUANTITY_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    static {
        FRENCH_QUANTITY_FORMAT.setMinimumFractionDigits(2);
        FRENCH_QUANTITY_FORMAT.setMaximumFractionDigits(2);
        FRENCH_QUANTITY_FORMAT.setGroupingUsed(true);
    }
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    /**
     * Formate un montant en format français (ex: 1 515,83)
     */
    public static String formatAmount(Double amount) {
        if (amount == null) return "0,00";
        return FRENCH_NUMBER_FORMAT.format(amount);
    }
    
    /**
     * Formate une quantité en format français (ex: 1 515,83)
     */
    public static String formatQuantity(Double qty) {
        if (qty == null) return "0,00";
        return FRENCH_QUANTITY_FORMAT.format(qty);
    }
    
    /**
     * Formate une date en format français (ex: 15/01/2024)
     */
    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * Convertit un nombre en toutes lettres (pour montants en lettres)
     */
    public static String numberToWords(double amount) {
        // Implémentation simplifiée - peut être améliorée avec une bibliothèque dédiée
        if (amount == 0) return "zéro";
        
        long entier = (long) amount;
        long decimal = Math.round((amount - entier) * 100);
        
        StringBuilder result = new StringBuilder();
        
        if (entier > 0) {
            result.append(convertNumberToWords(entier));
            result.append(" dirhams");
        }
        
        if (decimal > 0) {
            if (entier > 0) result.append(" et ");
            result.append(convertNumberToWords(decimal));
            result.append(" centimes");
        }
        
        return result.toString();
    }
    
    private static String convertNumberToWords(long number) {
        // Implémentation basique - peut être remplacée par une bibliothèque plus complète
        if (number < 20) {
            String[] units = {"zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
                    "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"};
            return units[(int) number];
        }
        
        if (number < 100) {
            long tens = number / 10;
            long units = number % 10;
            String[] tensNames = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante-dix", "quatre-vingt", "quatre-vingt-dix"};
            String result = tensNames[(int) tens];
            if (units > 0) {
                result += "-" + convertNumberToWords(units);
            }
            return result;
        }
        
        // Pour les nombres >= 100, on peut utiliser une bibliothèque dédiée
        return String.valueOf(number);
    }
}

