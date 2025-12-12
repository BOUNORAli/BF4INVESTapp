package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "produits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    private String id;
    
    private String refArticle;
    private String designation;
    private String unite; // sac, palette, M3, etc.
    
    private Double prixAchatUnitaireHT;
    private Double prixVenteUnitaireHT;
    private Double tva; // en pourcentage (ex: 20.0)
    
    private String fournisseurId; // Référence au fournisseur
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




