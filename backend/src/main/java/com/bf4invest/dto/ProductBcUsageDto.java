package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Un bon de commande où le produit apparaît (lignes agrégées sur ce BC).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBcUsageDto {
    private String bandeCommandeId;
    private String numeroBC;
    private LocalDate dateBC;
    /** Quantités achetées cumulées sur ce BC pour ce produit */
    private Double quantiteAcheteeTotale;
    /** Prix d'achat unitaire moyen pondéré sur ce BC */
    private Double prixAchatUnitaireHtPondere;
    private Double quantiteVendueTotale;
    private Double prixVenteUnitaireHtPondere;
    /** Identifiants fournisseurs concernés, séparés par virgule */
    private String fournisseurIds;
    /** Identifiants clients concernés, séparés par virgule */
    private String clientIds;
}
