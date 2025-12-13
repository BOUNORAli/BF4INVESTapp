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

@Document(collection = "bandes_commandes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BandeCommande {
    @Id
    private String id;
    
    private String numeroBC; // Format: BF4-BC-YYYY-NNNN
    private LocalDate dateBC;
    
    // Fournisseur unique
    private String fournisseurId;
    
    // ===== NOUVEAU: Structure multi-clients =====
    
    // Lignes d'achat communes (auprès du fournisseur)
    private List<LigneAchat> lignesAchat;
    
    // Clients avec leurs lignes de vente respectives
    private List<ClientVente> clientsVente;
    
    // ===== COMPATIBILITE ANCIENNE STRUCTURE =====
    // Ces champs sont conservés pour la rétrocompatibilité
    // et seront migrés vers la nouvelle structure
    
    @Deprecated
    private String clientId; // Ancien champ - sera migré vers clientsVente
    
    @Deprecated
    private List<LineItem> lignes; // Ancien champ - sera migré vers lignesAchat/clientsVente
    
    // ===== FIN COMPATIBILITE =====
    
    private String etat; // brouillon, envoyee, complete
    private String notes;
    private String modePaiement; // virement, cheque, LCN, compensation, etc.
    
    // Totaux calculés globaux
    private Double totalAchatHT;
    private Double totalAchatTTC;
    private Double totalVenteHT;      // Somme de tous les clients
    private Double totalVenteTTC;     // Somme de tous les clients
    private Double margeTotale;
    private Double margePourcentage;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Vérifie si le BC utilise la nouvelle structure multi-clients
     */
    public boolean isMultiClient() {
        return clientsVente != null && !clientsVente.isEmpty();
    }
    
    /**
     * Retourne le nombre de clients dans ce BC
     */
    public int getNombreClients() {
        if (clientsVente != null && !clientsVente.isEmpty()) {
            return clientsVente.size();
        }
        // Compatibilité: ancien format avec un seul client
        return clientId != null ? 1 : 0;
    }
}
