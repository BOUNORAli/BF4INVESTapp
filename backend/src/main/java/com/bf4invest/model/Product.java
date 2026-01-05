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
    
    // Anciens champs (conservés pour rétrocompatibilité/migration)
    @Deprecated
    private Double prixAchatUnitaireHT;
    @Deprecated
    private Double prixVenteUnitaireHT;
    
    // Nouveaux champs : prix pondérés (moyenne pondérée par quantité)
    private Double prixAchatPondereHT; // Prix d'achat pondéré calculé depuis toutes les BC
    private Double prixVentePondereHT; // Prix de vente pondéré calculé depuis toutes les BC
    
    private Double tva; // en pourcentage (ex: 20.0)
    
    private LocalDateTime derniereMiseAJourPrix; // Date de dernière mise à jour des prix pondérés
    
    private String fournisseurId; // Référence au fournisseur
    
    private Integer quantiteEnStock; // Quantité en stock (défaut: 0)
    
    private String imageBase64; // Image du produit en base64 (optionnel)
    private String imageContentType; // Type MIME de l'image (image/png, image/jpeg, etc.)
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




