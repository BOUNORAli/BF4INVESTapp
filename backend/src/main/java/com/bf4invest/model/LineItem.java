package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItem {
    private String produitRef;
    private String designation;
    private String unite;
    
    private Integer quantiteAchetee;
    private Integer quantiteVendue;
    
    private Double prixAchatUnitaireHT;
    private Double prixVenteUnitaireHT;
    private Double tva; // en pourcentage
    
    // Calculés (pour faciliter les requêtes)
    private Double totalHT;
    private Double totalTTC;
    private Double margeUnitaire;
    private Double margePourcentage;
}




