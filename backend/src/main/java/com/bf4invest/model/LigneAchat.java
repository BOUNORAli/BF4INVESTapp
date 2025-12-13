package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente une ligne d'achat auprès du fournisseur.
 * Ces lignes sont communes et partagées entre tous les clients du BC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneAchat {
    private String produitRef;
    private String designation;
    private String unite;
    
    private Double quantiteAchetee;
    private Double prixAchatUnitaireHT;
    private Double tva; // en pourcentage
    
    // Calculés
    private Double totalHT;
    private Double totalTTC;
}

