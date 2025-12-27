package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "operations_comptables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationComptable {
    @Id
    private String id;
    
    // Identification
    private String numeroBc;           // AFFECTATION/N°BC
    private String releveBancaire;     // RELEVE BQ (BM, etc.)
    
    // Partenaire
    private String contrePartie;       // CONTRE PARTIE
    private String nomClientFrs;       // NOM CLIENT/FRS
    private TypeOperation typeOperation; // C, F, IS, TVA, CNSS, FB, LOY
    
    // Opération
    private String sourcePayement;     // BM, Caisse, COMPENSATION
    private LocalDate dateOperation;   // DATE
    private TypeMouvement typeMouvement; // Facture ou Paiement
    private String numeroFacture;      // N° FACTURE
    private String reference;          // REFERENCE
    
    // Montants
    private Double totalTtcApresRg;    // TOTAL TTC APRES RG
    private Double totalPayementTtc;   // Total payement TTC
    private Double tauxTva;            // Taux TVA
    private Double tauxRg;             // TAUX RG
    
    // Paiement
    private String moyenPayement;      // LCN, SOLDEE, etc.
    private String commentaire;        // COMMENT AIRE
    
    // Période fiscale
    private Integer tvaMois;
    private Integer annee;
    private Integer mois;
    
    // Soldes et calculs
    private Double soldeBanque;
    private Double totalTtcApresRgCalcule;
    private Double totalPayementTtcCalcule;
    private Double rgTtc;
    private Double rgHt;
    private Double factureHtYcRg;
    private Double htPaye;
    private Double facture;
    private Double tvaYcRg;
    private Double tva;
    private Double bilan;
    private String ca;
    
    private LocalDateTime createdAt;
}

