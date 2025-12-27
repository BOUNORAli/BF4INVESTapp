package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {
    private int totalRows;
    private int successCount;
    private int errorCount;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    /**
     * Lignes en erreur avec leurs données originales
     * Clé: numéro de ligne (1-based)
     * Valeur: Map des données de la ligne (colonne -> valeur) + message d'erreur
     */
    @Builder.Default
    private List<ErrorRow> errorRows = new ArrayList<>();
    
    /**
     * Lignes importées avec succès (optionnel, pour le rapport)
     * Clé: numéro de ligne (1-based)
     * Valeur: Map des données de la ligne (colonne -> valeur)
     */
    @Builder.Default
    private List<SuccessRow> successRows = new ArrayList<>();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorRow {
        private int rowNumber; // Numéro de ligne dans le fichier Excel (1-based)
        private Map<String, Object> rowData; // Données de la ligne (colonne -> valeur)
        private String errorMessage; // Message d'erreur
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessRow {
        private int rowNumber; // Numéro de ligne dans le fichier Excel (1-based)
        private Map<String, Object> rowData; // Données de la ligne (colonne -> valeur)
    }
}


