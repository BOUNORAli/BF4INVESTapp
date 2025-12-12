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
    
    private String clientId;
    private String fournisseurId;
    
    private List<LineItem> lignes;
    
    private String etat; // brouillon, envoyee, complete
    private String notes;
    private String modePaiement; // virement, cheque, LCN, compensation, etc.
    
    // Totaux calculés (pour faciliter les requêtes)
    private Double totalAchatHT;
    private Double totalAchatTTC;
    private Double totalVenteHT;
    private Double totalVenteTTC;
    private Double margeTotale;
    private Double margePourcentage;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




