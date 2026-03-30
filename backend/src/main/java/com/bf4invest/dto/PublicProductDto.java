package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO public exposé sur le site vitrine.
 * Ne contient que les informations nécessaires pour l'affichage catalogue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProductDto {
    private String id;
    private String refArticle;
    private String designation;
    private String unite;
    private Double prixVentePondereHT;
    private Double tva;
    private Double quantiteEnStock;
    private String imageBase64;
    private String imageContentType;
    private String categorie;
}

