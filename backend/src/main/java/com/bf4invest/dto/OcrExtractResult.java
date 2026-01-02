package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrExtractResult {
    private String rawText;           // Texte brut extrait
    private String numeroDocument;     // Numéro facture/BC détecté
    private String dateDocument;       // Date détectée
    private String fournisseurNom;     // Nom fournisseur détecté
    private List<OcrProductLine> lignes; // Produits extraits
    private double confidence;         // Score de confiance
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrProductLine {
        private String designation;
        private Double quantite;
        private Double prixUnitaireHT;
        private Double prixTotalHT;
        private String unite;
    }
}

