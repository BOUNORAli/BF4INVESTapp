package com.bf4invest.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilitaire pour l'arrondi scientifique des nombres à 2 décimales.
 * Utilise RoundingMode.HALF_UP pour un arrondi standard (0.5 arrondit vers le haut).
 */
public class NumberUtils {
    
    private static final int DECIMAL_PLACES = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    /**
     * Arrondit un Double à 2 décimales de manière scientifique (HALF_UP).
     * Retourne null si la valeur est null.
     * 
     * @param value la valeur à arrondir
     * @return la valeur arrondie à 2 décimales, ou null si value est null
     */
    public static Double roundTo2Decimals(Double value) {
        if (value == null) {
            return null;
        }
        return roundTo2Decimals(value.doubleValue());
    }
    
    /**
     * Arrondit un double primitif à 2 décimales de manière scientifique (HALF_UP).
     * 
     * @param value la valeur à arrondir
     * @return la valeur arrondie à 2 décimales
     */
    public static double roundTo2Decimals(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(DECIMAL_PLACES, ROUNDING_MODE).doubleValue();
    }
    
    /**
     * Arrondit un Float à 2 décimales de manière scientifique (HALF_UP).
     * Retourne null si la valeur est null.
     * 
     * @param value la valeur à arrondir
     * @return la valeur arrondie à 2 décimales, ou null si value est null
     */
    public static Float roundTo2Decimals(Float value) {
        if (value == null) {
            return null;
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(DECIMAL_PLACES, ROUNDING_MODE).floatValue();
    }
    
    /**
     * Arrondit un float primitif à 2 décimales de manière scientifique (HALF_UP).
     * 
     * @param value la valeur à arrondir
     * @return la valeur arrondie à 2 décimales
     */
    public static float roundTo2Decimals(float value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(DECIMAL_PLACES, ROUNDING_MODE).floatValue();
    }
}
