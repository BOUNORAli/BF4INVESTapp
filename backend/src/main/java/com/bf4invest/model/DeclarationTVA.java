package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "declarations_tva")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclarationTVA {
    @Id
    private String id;
    
    private Integer mois; // 1-12
    private Integer annee;
    private String periode; // Format: "MM/YYYY" (ex: "01/2025")
    
    // TVA collectée (ventes)
    private Double tvaCollectee20; // TVA collectée au taux 20%
    private Double tvaCollectee14; // TVA collectée au taux 14%
    private Double tvaCollectee10; // TVA collectée au taux 10%
    private Double tvaCollectee7;  // TVA collectée au taux 7%
    private Double tvaCollectee0;  // TVA collectée au taux 0% (exonéré)
    private Double tvaCollecteeTotale; // Total TVA collectée
    
    // TVA déductible (achats)
    private Double tvaDeductible20; // TVA déductible au taux 20%
    private Double tvaDeductible14; // TVA déductible au taux 14%
    private Double tvaDeductible10; // TVA déductible au taux 10%
    private Double tvaDeductible7;  // TVA déductible au taux 7%
    private Double tvaDeductible0;  // TVA déductible au taux 0%
    private Double tvaDeductibleTotale; // Total TVA déductible
    
    // Résultat
    private Double tvaAPayer; // TVA à payer (si positif)
    private Double tvaCredit; // TVA crédit (si négatif, reporté au mois suivant)
    
    private StatutDeclaration statut; // BROUILLON, VALIDEE, DEPOSEE
    private LocalDate dateDepot; // Date de dépôt à la DGI
    
    private String notes;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum StatutDeclaration {
        BROUILLON,  // En cours de saisie
        VALIDEE,    // Validée mais pas encore déposée
        DEPOSEE     // Déposée à la DGI
    }
}

