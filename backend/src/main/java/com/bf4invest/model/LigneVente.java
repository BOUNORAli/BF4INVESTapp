package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente une ligne de vente pour un client spécifique.
 * Chaque client peut avoir des prix de vente différents pour le même produit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneVente {
    private String produitRef;
    private String designation;
    private String unite;
    
    private Double quantiteVendue;
    private Double prixVenteUnitaireHT;
    private Double tva; // en pourcentage
    
    // Calculés
    private Double totalHT;
    private Double totalTTC;
    private Double margeUnitaire;      // par rapport au prix d'achat
    private Double margePourcentage;   // par rapport au prix d'achat
}

