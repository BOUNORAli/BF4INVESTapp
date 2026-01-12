package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Représente un fournisseur avec ses lignes d'achat dans un Bon de Commande.
 * Permet d'avoir plusieurs fournisseurs avec des prix d'achat différents pour chaque BC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FournisseurAchat {
    private String fournisseurId;
    
    // Lignes d'achat spécifiques à ce fournisseur
    private List<LigneAchat> lignesAchat;
    
    // Totaux calculés pour ce fournisseur
    private Double totalAchatHT;
    private Double totalAchatTTC;
    private Double totalTVA;
}
