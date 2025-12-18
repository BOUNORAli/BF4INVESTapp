package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Représente un client avec ses lignes de vente dans un Bon de Commande.
 * Permet d'avoir plusieurs clients avec des prix de vente différents pour chaque BC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientVente {
    private String clientId;
    
    // Lignes de vente spécifiques à ce client
    private List<LigneVente> lignesVente;
    
    // Totaux calculés pour ce client
    private Double totalVenteHT;
    private Double totalVenteTTC;
    private Double totalTVA;
    private Double margeTotale;
    private Double margePourcentage;
}



