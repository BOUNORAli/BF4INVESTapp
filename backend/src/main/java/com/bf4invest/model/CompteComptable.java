package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "comptes_comptables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompteComptable {
    @Id
    private String id;
    
    private String code; // Ex: 6111, 7111, 4455
    private String libelle; // Ex: "Achats de matières premières"
    private String classe; // 1, 2, 3, 4, 5, 6, 7
    private TypeCompte type; // ACTIF, PASSIF, CHARGE, PRODUIT, TRESORERIE
    private Boolean collectif; // true si compte collectif (ex: 611)
    private String compteParent; // Code du compte parent si sous-compte
    
    private Double soldeDebit; // Total débit
    private Double soldeCredit; // Total crédit
    private Double solde; // Solde (débit - crédit, signé selon le type)
    
    private Boolean actif; // true si le compte est actif/utilisé
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum TypeCompte {
        ACTIF,      // Classe 2, 3, 5 (immobilisations, stocks, trésorerie)
        PASSIF,     // Classe 1, 4 (capitaux, dettes)
        CHARGE,     // Classe 6
        PRODUIT,    // Classe 7
        TRESORERIE  // Classe 5 (banque, caisse)
    }
}

