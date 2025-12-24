package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "ecritures_comptables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcritureComptable {
    @Id
    private String id;
    
    private LocalDate dateEcriture;
    private String journal; // VT (ventes), AC (achats), OD (opérations diverses), BQ (banque)
    private String numeroPiece; // Numéro de pièce comptable
    private String libelle; // Libellé de l'écriture
    
    private List<LigneEcriture> lignes; // Lignes débit/crédit
    
    // Pièce justificative
    private String pieceJustificativeType; // FACTURE_VENTE, FACTURE_ACHAT, PAIEMENT, CHARGE, etc.
    private String pieceJustificativeId; // ID de la facture, paiement, charge, etc.
    
    private String exerciceId; // ID de l'exercice comptable
    private Boolean lettree; // true si l'écriture est lettrée (rapprochement bancaire)
    private Boolean pointage; // true si l'écriture est pointée
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

