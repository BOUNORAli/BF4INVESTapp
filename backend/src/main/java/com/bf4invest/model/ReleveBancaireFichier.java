package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "releves_bancaires_fichiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleveBancaireFichier {
    @Id
    private String id;
    
    private String fichierId;         // ID GridFS du fichier PDF
    private String nomFichier;        // Nom original du fichier
    private String contentType;       // MIME type (application/pdf)
    private Long taille;               // Taille du fichier en bytes
    private String url;               // URL sécurisée Cloudinary
    
    private Integer mois;              // Mois du relevé (1-12)
    private Integer annee;             // Année du relevé
    
    private LocalDateTime uploadedAt;  // Date d'upload
    private String uploadedBy;         // ID de l'utilisateur qui a uploadé (optionnel)
}

