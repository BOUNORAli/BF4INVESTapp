package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "transactions_bancaires")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionBancaire {
    @Id
    private String id;
    
    // Dates du relevé bancaire
    private LocalDate dateOperation; // Colonne "Date" du relevé
    private LocalDate dateValeur; // Colonne "Valeur" du relevé (optionnelle)
    
    // Informations de la transaction
    private String libelle; // Colonne "Libellé Opération"
    private Double debit; // Colonne "Débit"
    private Double credit; // Colonne "Crédit"
    private String reference; // Numéro de chèque, référence virement, etc.
    
    // Liens avec les factures et paiements
    private String factureVenteId; // Lien optionnel vers une facture vente
    private String factureAchatId; // Lien optionnel vers une facture achat
    private String paiementId; // Lien optionnel vers un paiement créé depuis cette transaction
    
    // Statut de mapping
    private Boolean mapped; // Si la transaction a été mappée à une facture/paiement
    
    // Métadonnées
    private Integer mois; // Mois de la transaction (pour faciliter les recherches)
    private Integer annee; // Année de la transaction
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

