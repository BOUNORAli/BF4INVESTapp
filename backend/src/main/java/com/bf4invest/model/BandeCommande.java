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
    
    // ===== NOUVEAU: Structure multi-fournisseurs =====
    
    // Fournisseurs avec leurs lignes d'achat respectives
    private List<FournisseurAchat> fournisseursAchat;
    
    // ===== COMPATIBILITE ANCIENNE STRUCTURE FOURNISSEUR =====
    
    @Deprecated
    private String fournisseurId; // Ancien champ - sera migré vers fournisseursAchat
    
    @Deprecated
    private List<LigneAchat> lignesAchat; // Ancien champ - sera migré vers fournisseursAchat
    
    // ===== NOUVEAU: Structure multi-clients =====
    
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
    private String modePaiement; // virement, cheque, LCN, compensation, etc. (type de paiement)
    private String delaiPaiement; // Délai de paiement en jours (ex: "120J", "30J", etc.)

    // Infos livraison (doivent venir du BC pour le PDF)
    private String lieuLivraison;
    private String conditionLivraison;
    private String responsableLivraison;
    
    private Boolean ajouterAuStock; // Option pour ajouter les quantités achetées au stock (défaut: false)
    
    // Totaux calculés globaux
    private Double totalAchatHT;
    private Double totalAchatTTC;
    private Double totalVenteHT;      // Somme de tous les clients
    private Double totalVenteTTC;     // Somme de tous les clients
    private Double margeTotale;
    private Double margePourcentage;
    
    // Totaux bruts depuis Excel (pour préserver les valeurs exactes du fichier)
    private Double totalAchatTTCFromExcel;  // Total achat TTC depuis "facture_achat_ttc"
    private Double totalVenteTTCFromExcel;  // Total vente TTC depuis "facture_vente_ttc" (somme des factures vente)
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Vérifie si le BC utilise la nouvelle structure multi-fournisseurs
     */
    public boolean isMultiFournisseur() {
        return fournisseursAchat != null && !fournisseursAchat.isEmpty();
    }
    
    /**
     * Retourne le nombre de fournisseurs dans ce BC
     */
    public int getNombreFournisseurs() {
        if (fournisseursAchat != null && !fournisseursAchat.isEmpty()) {
            return fournisseursAchat.size();
        }
        // Compatibilité: ancien format avec un seul fournisseur
        return fournisseurId != null ? 1 : 0;
    }
    
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
