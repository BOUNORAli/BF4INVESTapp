package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "historique_solde")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueSolde {
    @Id
    private String id;
    
    private String type; // "FACTURE_VENTE", "FACTURE_ACHAT", "PAIEMENT_CLIENT", "PAIEMENT_FOURNISSEUR"
    private Double montant; // Montant de la transaction
    
    // Évolution du solde global
    private Double soldeGlobalAvant;
    private Double soldeGlobalApres;
    
    // Évolution du solde partenaire (client ou fournisseur)
    private Double soldePartenaireAvant;
    private Double soldePartenaireApres;
    
    // Référence au partenaire
    private String partenaireId; // ID du client ou fournisseur
    private String partenaireType; // "CLIENT" ou "FOURNISSEUR"
    private String partenaireNom; // Nom du partenaire (pour faciliter l'affichage)
    
    // Référence à la transaction source
    private String referenceId; // ID de la facture ou paiement source
    private String referenceNumero; // Numéro de la facture ou référence du paiement
    
    private LocalDateTime date;
    private String description; // Description optionnelle de la transaction
}


